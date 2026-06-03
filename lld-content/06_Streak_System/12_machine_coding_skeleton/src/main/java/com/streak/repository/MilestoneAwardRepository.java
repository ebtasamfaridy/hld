package com.streak.repository;

import com.streak.domain.MilestoneAward;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.util.List;

public interface MilestoneAwardRepository {
    /** Inserts only if (user, type, days) doesn't already exist. */
    boolean saveIfAbsent(MilestoneAward award);

    List<MilestoneAward> findByUser(UserId userId);

    boolean exists(UserId userId, StreakType type, int days);
}
