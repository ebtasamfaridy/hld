package com.streak.repository;

import com.streak.domain.StreakState;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.util.Optional;

public interface StreakStateRepository {
    Optional<StreakState> findByUserAndType(UserId userId, StreakType type);

    /** Compare-and-swap: succeeds only if stored version == updated.version()-1. */
    boolean saveWithCas(StreakState updated);
}
