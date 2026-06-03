package com.streak.domain;

/**
 * Discriminated union of possible outcomes when an activity event is applied
 * to a StreakState. Application layer pattern-matches on the variant.
 */
public sealed interface StreakUpdate
        permits StreakUpdate.NoOp,
                StreakUpdate.Advanced,
                StreakUpdate.Restarted,
                StreakUpdate.Backfilled {

    StreakState state();

    /** Same calendar day as last activity — already counted. */
    record NoOp(StreakState state) implements StreakUpdate {}

    /** Streak grew by one (eventDay = lastActiveDay + 1). */
    record Advanced(StreakState state, int previousCurrent, int newCurrent)
            implements StreakUpdate {}

    /** Gap > 1 day. Streak broken; restarted at 1. */
    record Restarted(StreakState state, int previousCurrent, int previousLongest)
            implements StreakUpdate {}

    /** Event is for a day before lastActiveDay — calendar only, no streak math. */
    record Backfilled(StreakState state) implements StreakUpdate {}
}
