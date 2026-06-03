package com.ratelimit.domain;

public record LimitConfig(
        long maxTokens,        // capacity / burst
        double refillPerSec,   // for Token / Leaky bucket
        long windowSeconds     // for Fixed / Sliding window
) {
    public static LimitConfig tokenBucket(long burst, double rate) {
        return new LimitConfig(burst, rate, 0);
    }
    public static LimitConfig fixedWindow(long limit, long windowSec) {
        return new LimitConfig(limit, 0, windowSec);
    }
}
