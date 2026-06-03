package com.streak.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
    public UserId { Objects.requireNonNull(value); }
    public static UserId of(String s) { return new UserId(UUID.fromString(s)); }
    public static UserId random() { return new UserId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
