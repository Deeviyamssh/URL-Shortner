package com.example.urlshortener.ratelimit;

import com.example.urlshortener.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the registration rate‑limit Lua script.
 * Verifies that the script increments, sets TTL on first increment,
 * and returns -1 when the limit is reached.
 */
class RegistrationRateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final DefaultRedisScript<Long> script;

    public RegistrationRateLimitIntegrationTest() {
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText("""
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local windowSeconds = tonumber(ARGV[2])

            local current = redis.call('GET', key)
            if current == false then
                redis.call('SET', key, 1, 'EX', windowSeconds)
                return 1
            end

            local count = tonumber(current)
            if count >= limit then
                return -1
            end

            redis.call('INCR', key)
            return count + 1
        """);
        this.script.setResultType(Long.class);
    }

    @Test
    void script_firstIncrement_returns1AndSetsTTL() {
        String key = "test:reg:script-1-" + System.nanoTime();
        Long result = redisTemplate.execute(script,
                Collections.singletonList(key),
                "5", "10"); // limit 5, window 10 seconds

        assertThat(result).isEqualTo(1L);

        // Verify TTL is set and > 0
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(10);

        // Clean up
        redisTemplate.delete(key);
    }

    @Test
    void script_subsequentIncrements_returnIncreasedCount() {
        String key = "test:reg:script-2-" + System.nanoTime();
        // First increment
        Long r1 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "5", "10");
        assertThat(r1).isEqualTo(1L);

        // Second increment
        Long r2 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "5", "10");
        assertThat(r2).isEqualTo(2L);

        // Third increment
        Long r3 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "5", "10");
        assertThat(r3).isEqualTo(3L);

        // Verify TTL still present
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);

        redisTemplate.delete(key);
    }

    @Test
    void script_reachesLimit_returnsNegativeOne() {
        String key = "test:reg:script-3-" + System.nanoTime();
        // Set limit to 2
        Long r1 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "2", "10");
        assertThat(r1).isEqualTo(1L);

        Long r2 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "2", "10");
        assertThat(r2).isEqualTo(2L);

        // Third attempt should be -1 (limit reached)
        Long r3 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "2", "10");
        assertThat(r3).isEqualTo(-1L);

        // Count should still be 2
        String current = redisTemplate.opsForValue().get(key);
        assertThat(current).isEqualTo("2");

        redisTemplate.delete(key);
    }

    @Test
    void script_afterTTLExpires_resetsCount() throws InterruptedException {
        String key = "test:reg:script-4-" + System.nanoTime();
        // Use a tiny window (2 seconds) for test speed
        Long r1 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "3", "2");
        assertThat(r1).isEqualTo(1L);

        // Wait for TTL to expire
        Thread.sleep(2500);

        // Should behave like first increment again
        Long r2 = redisTemplate.execute(script,
                Collections.singletonList(key),
                "3", "2");
        assertThat(r2).isEqualTo(1L); // not 2, because key expired

        redisTemplate.delete(key);
    }
}