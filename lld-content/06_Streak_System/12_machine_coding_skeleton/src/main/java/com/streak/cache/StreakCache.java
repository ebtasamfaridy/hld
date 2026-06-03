package com.streak.cache;

import com.streak.domain.StreakState;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;

public interface StreakCache {

    /**
     * Atomic dedup: returns true ONLY if (user, type, day) hasn't been
     * seen before in the cache window. Models Redis SETNX with TTL.
     */
    boolean tryDedup(UserId userId, StreakType type, LocalDate day);

    Optional<StreakState> getStreak(UserId userId, StreakType type);

    void putStreak(StreakState state);

    /** Bumps the cache version, invalidating all keyed reads. */
    void invalidateAllStreaks();

    StreakType getActiveType();

    void setActiveType(StreakType type);
}
