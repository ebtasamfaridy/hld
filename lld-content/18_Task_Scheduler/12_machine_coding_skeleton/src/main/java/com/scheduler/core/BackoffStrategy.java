package com.scheduler.core;

import java.time.Duration;

public interface BackoffStrategy {
    Duration delay(int attempt);     // 1-based: first retry = attempt 2 (already attempted 1)
}
