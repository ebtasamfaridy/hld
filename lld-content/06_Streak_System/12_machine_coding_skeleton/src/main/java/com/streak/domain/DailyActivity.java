package com.streak.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record DailyActivity(
        UserId userId,
        StreakType streakType,
        LocalDate day,
        int eventCount,
        Instant firstEventAt,
        Instant lastEventAt,
        ZoneId userTimezone
) {
    public DailyActivity incrementWith(Instant eventAt) {
        return new DailyActivity(userId, streakType, day,
                eventCount + 1,
                firstEventAt,
                eventAt.isAfter(lastEventAt) ? eventAt : lastEventAt,
                userTimezone);
    }
}
