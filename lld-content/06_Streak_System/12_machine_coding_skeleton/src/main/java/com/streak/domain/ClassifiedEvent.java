package com.streak.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record ClassifiedEvent(
        UserId userId,
        StreakType type,
        Instant occurredAt,
        ZoneId userTimezone
) {
    public LocalDate eventDay() {
        return occurredAt.atZone(userTimezone).toLocalDate();
    }
}
