package com.streak.repository;

import com.streak.domain.DailyActivity;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface DailyActivityRepository {

    /**
     * Idempotent on (userId, type, day). If row exists, increments count
     * and updates lastEventAt; otherwise inserts a new row.
     */
    void incrementOrCreate(UserId userId, StreakType type, LocalDate day,
                           Instant eventAt, ZoneId tz);

    /** All activity rows for a user/type within [from, to] inclusive. */
    List<DailyActivity> findInRange(UserId userId, StreakType type,
                                    LocalDate from, LocalDate to);
}
