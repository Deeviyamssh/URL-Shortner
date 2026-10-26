package com.example.urlshortener.service;

import com.example.urlshortener.dto.RegistrationRequest;
import com.example.urlshortener.dto.RegistrationResponse;
import com.example.urlshortener.entity.ApiKey;
import com.example.urlshortener.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles user self-service registration.
 *
 * The registration flow:
 * 1. Validate that the username is not already taken (application-level check).
 * 2. Check the registration rate limit via an atomic Redis Lua script with TTL.
 * 3. Generate a cryptographically secure random access key.
 * 4. BCrypt-hash the password and store the ApiKey entity.
 * 5. Return the plaintext access key once — it is never persisted or logged again.
 *
 * Rate limiting: 3 registrations per IP per hour.  Uses an atomic Redis counter
 * with a 1-hour TTL.  If Redis is unavailable, registration is rejected with
 * HTTP 503 rather than silently disabling the limit.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final int RATE_LIMIT_WINDOW_SECONDS = 3600; // 1 hour
    private static final int ACCESS_KEY_LENGTH = 24;
    private static final String RATE_LIMIT_PREFIX = "reg:";
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom random = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @org.springframework.beans.factory.annotation.Value("${app.registration.max-per-hour:3}")
    private int maxRegistrationsPerHour;

    @org.springframework.beans.factory.annotation.Value("${app.trusted-proxy-ips:}")
    private List<String> trustedProxyIps;

    private final DefaultRedisScript<Long> registrationRateLimitScript;

    public RegistrationService(ApiKeyRepository apiKeyRepository,
                                RedisTemplate<String, String> redisTemplate) {
        this.apiKeyRepository = apiKeyRepository;
        this.redisTemplate = redisTemplate;
        this.registrationRateLimitScript = new DefaultRedisScript<>();
        this.registrationRateLimitScript.setScriptText("""
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
        this.registrationRateLimitScript.setResultType(Long.class);
    }

    /**
     * Resolves the real client IP address, validating X-Forwarded-For only
     * when the immediate remote address is in the trusted-proxy list.
     *
     * If the remote address is not trusted, X-Forwarded-For is ignored,
     * preventing clients from spoofing the header to bypass rate limiting.
     *
     * The first valid IP from X-Forwarded-For is used; subsequent entries are
     * discarded because they represent earlier proxy hops.
     */
    String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // If the remote address is not a trusted proxy, ignore X-Forwarded-For.
        if (trustedProxyIps == null || trustedProxyIps.isEmpty() || !trustedProxyIps.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedHeader = request.getHeader("X-Forwarded-For");
        if (forwardedHeader == null || forwardedHeader.isBlank()) {
            return remoteAddr;
        }

        // X-Forwarded-For is comma-separated; the leftmost entry is the original client.
        String[] forwardedIps = forwardedHeader.split(",");
        for (String ip : forwardedIps) {
            String trimmed = ip.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("unknown")) {
                return trimmed;
            }
        }

        // No valid IP found in the header — fall back to remote address.
        return remoteAddr;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername().trim();
        String password = request.getPassword();

        // 1. Application-level duplicate check
        if (apiKeyRepository.findByOwner(username) != null) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }

        // 2. Registration rate limit (atomic Lua script with SET+EX on first increment)
        String clientIp = resolveClientIp(httpRequest);
        String rateLimitKey = RATE_LIMIT_PREFIX + clientIp;
        try {
            Long count = redisTemplate.execute(
                    registrationRateLimitScript,
                    Collections.singletonList(rateLimitKey),
                    String.valueOf(maxRegistrationsPerHour),
                    String.valueOf(RATE_LIMIT_WINDOW_SECONDS)
            );
            if (count == null) {
                // Should not happen with the script, but treat as Redis failure
                log.error("Registration rate-limit script returned null");
                throw new IllegalStateException("Registration is temporarily unavailable. Please try again later.");
            }
            if (count == -1L) {
                throw new IllegalStateException(
                    "Too many registrations from this IP. Try again later.");
            }
        } catch (IllegalStateException e) {
            throw e; // rate limit exceeded — re-throw as-is
        } catch (Exception e) {
            // Redis is unavailable — fail safely by rejecting the registration.
            log.error("Registration rate-limit check failed (Redis unavailable): {}", e.getMessage());
            throw new IllegalStateException("Registration is temporarily unavailable. Please try again later.");
        }

        // 3. Generate a cryptographically secure random access key
        String accessKey = generateAccessKey();

        // 4. Hash the password and save
        String hashedKey = passwordEncoder.encode(password);

        ApiKey apiKey = new ApiKey();
        apiKey.setOwner(username);
        apiKey.setAccessKey(accessKey);
        apiKey.setHashedKey(hashedKey);
        apiKey.setCreatedAt(LocalDateTime.now());

        try {
            apiKeyRepository.save(apiKey);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same username — the DB unique constraint caught it.
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }

        // 5. Build response — access key shown once
        RegistrationResponse response = new RegistrationResponse();
        response.setOwner(username);
        response.setAccessKey(accessKey);
        response.setMessage("Registration successful. Save your access key — you will need it along with your password to sign in. The access key will not be shown again.");
        return response;
    }

    private String generateAccessKey() {
        StringBuilder sb = new StringBuilder(ACCESS_KEY_LENGTH);
        for (int i = 0; i < ACCESS_KEY_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
