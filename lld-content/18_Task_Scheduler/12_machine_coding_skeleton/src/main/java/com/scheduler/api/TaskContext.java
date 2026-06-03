package com.scheduler.api;

import java.time.Instant;

public record TaskContext(String jobId, String jobName, Instant scheduledFor, int attempt, String idempotencyKey) {}
