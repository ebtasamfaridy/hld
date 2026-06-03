package com.ratelimit.domain;

import java.time.Duration;

public sealed interface Decision permits Decision.Allow, Decision.Deny {
    record Allow(long remaining, long resetEpochMs) implements Decision {}
    record Deny(Duration retryAfter, long limit, String violatedScope) implements Decision {}
}
