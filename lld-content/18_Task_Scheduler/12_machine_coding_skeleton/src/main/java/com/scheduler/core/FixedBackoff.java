package com.scheduler.core;

import java.time.Duration;

public final class FixedBackoff implements BackoffStrategy {
    private final Duration d;
    public FixedBackoff(Duration d) { this.d = d; }
    @Override public Duration delay(int attempt) { return d; }
}
