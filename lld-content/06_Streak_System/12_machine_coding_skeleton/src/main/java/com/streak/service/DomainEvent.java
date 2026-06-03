package com.streak.service;

import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;

public sealed interface DomainEvent
        permits DomainEvent.ActivityRecorded,
                DomainEvent.StreakAdvanced,
                DomainEvent.StreakBroken,
                DomainEvent.StreakMilestoneReached,
                DomainEvent.AdminConfigChanged {

    Instant occurredAt();

    record ActivityRecorded(UserId userId, StreakType type, LocalDate day,
                            boolean firstOfDay, Instant occurredAt)
            implements DomainEvent {}

    record StreakAdvanced(UserId userId, StreakType type,
                          int previousCurrent, int currentStreak, int longestStreak,
                          Instant occurredAt) implements DomainEvent {}

    record StreakBroken(UserId userId, StreakType type,
                        int previousCurrent, int previousLongest,
                        Instant occurredAt) implements DomainEvent {}

    record StreakMilestoneReached(UserId userId, StreakType type, int milestoneDays,
                                  Instant occurredAt) implements DomainEvent {}

    record AdminConfigChanged(StreakType oldType, StreakType newType,
                              Instant occurredAt) implements DomainEvent {}
}
