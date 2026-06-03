package com.streak.domain;

import java.time.Instant;

public record MilestoneAward(
        UserId userId,
        StreakType streakType,
        int milestoneDays,
        Instant achievedAt
) {}
