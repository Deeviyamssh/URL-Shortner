package com.example.urlshortener.ratelimit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class RateLimiterService {

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])  -- tokens per second
            local now = tonumber(ARGV[3])

            local hash = redis.call('HMGET', key, 'tokens', 'lastRefillTime')
            local tokens = tonumber(hash[1])
            local lastRefillTime = tonumber(hash[2])

            if tokens == nil then
                tokens = limit
                lastRefillTime = now
            end

            local tokensToAdd = (now - lastRefillTime) * refillRate
            tokens = math.min(limit, tokens + tokensToAdd)

            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tokens, 'lastRefillTime', now)
            redis.call('EXPIRE', key, math.ceil(limit / refillRate) * 2)  -- expire after 2 windows

            local timeToRefill = 0
            if tokens < limit then
                timeToRefill = (limit - tokens) / refillRate
            end

            return {allowed, tokens, timeToRefill}
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List<Object>> redisScript;

    public RateLimiterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptText(LUA_SCRIPT);
        this.redisScript.setResultType((Class<List<Object>>) (Class<?>) List.class);
    }

    /**
     * Consumes a token from the bucket identified by the given key.
     *
     * @param key           the unique identifier for the bucket (e.g., "ip:192.168.1.1")
     * @param limit         the maximum number of tokens in the bucket
     * @param windowSeconds the time window in seconds for the limit (refill rate = limit / windowSeconds)
     * @return a RateLimitResponse indicating whether the request was allowed and the remaining tokens
     */
    public RateLimitResponse consume(String key, int limit, int windowSeconds) {
        double refillRate = limit / (double) windowSeconds;
        long now = System.currentTimeMillis() / 1000; // current time in seconds

        List<Object> result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(refillRate),
                String.valueOf(now)
        );

        boolean allowed = ((Number) result.get(0)).intValue() == 1;
        double remainingTokens = ((Number) result.get(1)).doubleValue();
        double timeToRefillSeconds = ((Number) result.get(2)).doubleValue();

        return new RateLimitResponse(allowed, remainingTokens, timeToRefillSeconds);
    }
}