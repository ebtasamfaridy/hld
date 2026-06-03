package com.streak.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Aggregate root: per-user, per-type streak counter.
 *
 * Pure domain object — no persistence concerns. The recordActivity method
 * returns a StreakUpdate that the application layer interprets.
 */
public final class StreakState {

    private final UserId userId;
    private final StreakType streakType;
    private final int current;
    private final int longest;
    private final LocalDate lastActiveDay;     // null if never active
    private final ZoneId userTimezone;
    private final long version;
    private final Instant updatedAt;

    public StreakState(UserId userId, StreakType streakType,
                       int current, int longest,
                       LocalDate lastActiveDay, ZoneId userTimezone,
                       long version, Instant updatedAt) {
        if (current < 0) throw new IllegalArgumentException("current<0");
        if (longest < current) throw new IllegalArgumentException("longest<current");
        this.userId = Objects.requireNonNull(userId);
        this.streakType = Objects.requireNonNull(streakType);
        this.current = current;
        this.longest = longest;
        this.lastActiveDay = lastActiveDay;
        this.userTimezone = Objects.requireNonNull(userTimezone);
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static StreakState empty(UserId u, StreakType t, ZoneId tz) {
        return new StreakState(u, t, 0, 0, null, tz, 0, Instant.EPOCH);
    }

    /** The core streak algorithm. Pure: returns a new state, never mutates. */
    public StreakUpdate recordActivity(LocalDate eventDay, ZoneId tzAtEvent, Instant now) {
        if (lastActiveDay != null && eventDay.equals(lastActiveDay)) {
            return new StreakUpdate.NoOp(this);
        }
        if (lastActiveDay != null && eventDay.isBefore(lastActiveDay)) {
            return new StreakUpdate.Backfilled(this);
        }
        int newCurrent;
        boolean restarted;
        if (lastActiveDay == null || eventDay.isAfter(lastActiveDay.plusDays(1))) {
            newCurrent = 1;
            restarted = lastActiveDay != null;
        } else {
            newCurrent = current + 1;
            restarted = false;
        }
        int newLongest = Math.max(longest, newCurrent);
        StreakState next = new StreakState(
                userId, streakType, newCurrent, newLongest,
                eventDay, tzAtEvent, version + 1, now);
        if (restarted) {
            return new StreakUpdate.Restarted(next, current, longest);
        }
        return new StreakUpdate.Advanced(next, current, newCurrent);
    }

    /** Computed at read time; never stored. */
    public boolean isAlive(LocalDate today) {
        if (lastActiveDay == null) return false;
        long gap = ChronoUnit.DAYS.between(lastActiveDay, today);
        return gap >= 0 && gap <= 1;
    }

    public UserId userId() { return userId; }
    public StreakType streakType() { return streakType; }
    public int current() { return current; }
    public int longest() { return longest; }
    public LocalDate lastActiveDay() { return lastActiveDay; }
    public ZoneId userTimezone() { return userTimezone; }
    public long version() { return version; }
    public Instant updatedAt() { return updatedAt; }

    @Override public String toString() {
        return "StreakState{u=" + userId + " type=" + streakType
                + " cur=" + current + " best=" + longest
                + " last=" + lastActiveDay + " v=" + version + "}";
    }
}
