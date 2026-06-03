package com.elevator.domain;

import java.time.Instant;

public record HallCall(int floor, Direction direction, Instant pressedAt) {
    public static HallCall of(int f, Direction d) { return new HallCall(f, d, Instant.now()); }
}
