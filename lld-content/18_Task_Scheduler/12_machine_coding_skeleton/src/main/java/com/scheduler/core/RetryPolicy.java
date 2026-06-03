package com.scheduler.core;

import java.time.Duration;

public final class RetryPolicy {
    private final int maxAttempts;
    private final BackoffStrategy backoff;

    public RetryPolicy(int maxAttempts, BackoffStrategy backoff) {
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    public int maxAttempts()              { return maxAttempts; }
    public boolean shouldRetry(int done)  { return done < maxAttempts; }
    public Duration nextDelay(int attempt) { return backoff.delay(attempt); }

    public static RetryPolicy none() { return new RetryPolicy(1, new FixedBackoff(Duration.ZERO)); }
    public static RetryPolicy of(int maxAttempts, Duration baseDelay) {
        return new RetryPolicy(maxAttempts, new ExponentialBackoffWithJitter(baseDelay, Duration.ofSeconds(60), 0.25));
    }
}
