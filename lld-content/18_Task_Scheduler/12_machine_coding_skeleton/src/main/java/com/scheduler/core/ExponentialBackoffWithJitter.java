package com.scheduler.core;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class ExponentialBackoffWithJitter implements BackoffStrategy {

    private final Duration base;
    private final Duration cap;
    private final double jitterFraction;

    public ExponentialBackoffWithJitter(Duration base, Duration cap, double jitterFraction) {
        this.base = base; this.cap = cap; this.jitterFraction = jitterFraction;
    }

    @Override
    public Duration delay(int attempt) {
        double exp = Math.min(cap.toMillis(), base.toMillis() * Math.pow(2, attempt - 1));
        double jitter = exp * jitterFraction * (ThreadLocalRandom.current().nextDouble() * 2 - 1); // ±jitter
        long ms = Math.max(0, Math.round(exp + jitter));
        return Duration.ofMillis(ms);
    }
}
