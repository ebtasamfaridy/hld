package com.streak.domain;

import java.time.LocalDate;
import java.time.ZoneId;

/** Read-side DTO returned to clients from GET /v1/me/streak. */
public record StreakSnapshot(
        UserId userId,
        StreakType type,
        int current,
        int longest,
        LocalDate lastActiveDay,
        LocalDate today,
        boolean isAlive,
        ZoneId timezone
) {
    public static StreakSnapshot from(StreakState s, LocalDate today) {
        return new StreakSnapshot(
                s.userId(), s.streakType(), s.current(), s.longest(),
                s.lastActiveDay(), today, s.isAlive(today), s.userTimezone());
    }
}
