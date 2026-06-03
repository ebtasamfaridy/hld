package com.pubsub.domain;

import java.time.Instant;

public record Record(long offset, Instant timestamp, String key, String value) {}
