package com.example.urlshortener.ratelimit;

public class RateLimitResponse {
    private final boolean allowed;
    private final double remainingTokens;
    private final double timeToRefillSeconds; // time in seconds to refill the bucket to full

    public RateLimitResponse(boolean allowed, double remainingTokens, double timeToRefillSeconds) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.timeToRefillSeconds = timeToRefillSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public double getRemainingTokens() {
        return remainingTokens;
    }

    public double getTimeToRefillSeconds() {
        return timeToRefillSeconds;
    }
}