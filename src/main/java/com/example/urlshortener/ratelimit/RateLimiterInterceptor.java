package com.example.urlshortener.ratelimit;

import com.example.urlshortener.ratelimit.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterConfig rateLimiterConfig;
    private final String legacyApiKeyHeader;

    public RateLimiterInterceptor(RateLimiterService rateLimiterService, RateLimiterConfig rateLimiterConfig, @Value("${app.api-key-header:X-API-Key}") String legacyApiKeyHeader) {
        this.rateLimiterService = rateLimiterService;
        this.rateLimiterConfig = rateLimiterConfig;
        this.legacyApiKeyHeader = legacyApiKeyHeader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Check if there is an authentication in the security context (from our API key filter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String identifier;
        int limit;
        int windowSeconds;

        if (auth != null && auth.isAuthenticated()) {
            // Use the authenticated access key for API key-based rate limiting
            String accessKey = auth.getName(); // principal is the access key
            identifier = "api-key:" + accessKey;
            limit = rateLimiterConfig.getApiKey().getLimit();
            windowSeconds = rateLimiterConfig.getApiKey().getWindowHours() * 3600;
        } else {
            // Fall back to legacy header or IP address for rate limiting
            String apiKey = request.getHeader(legacyApiKeyHeader);
            boolean useApiKey = apiKey != null && !apiKey.isEmpty();

            if (useApiKey) {
                identifier = "api-key:" + apiKey;
                limit = rateLimiterConfig.getApiKey().getLimit();
                windowSeconds = rateLimiterConfig.getApiKey().getWindowHours() * 3600;
            } else {
                // Use IP address
                String ip = request.getRemoteAddr();
                identifier = "ip:" + ip;
                limit = rateLimiterConfig.getIp().getLimit();
                windowSeconds = rateLimiterConfig.getIp().getWindowMinutes() * 60;
            }
        }

        RateLimitResponse rateLimitResponse = rateLimiterService.consume(identifier, limit, windowSeconds);

        // Set headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.format("%.2f", rateLimitResponse.getRemainingTokens()));
        double resetTime = System.currentTimeMillis() / 1000.0 + rateLimitResponse.getTimeToRefillSeconds();
        response.setHeader("X-RateLimit-Reset", String.valueOf((long) resetTime));

        if (rateLimitResponse.isAllowed()) {
            return true;
        } else {
            // Calculate retry-after: time to wait for at least one token
            double refillRate = limit / (double) windowSeconds;
            double retryAfter = Math.max(0, (1 - rateLimitResponse.getRemainingTokens()) / refillRate);
            response.setHeader("Retry-After", String.valueOf((long) Math.ceil(retryAfter)));
            response.setStatus(429); // Too Many Requests
            return false;
        }
    }
}