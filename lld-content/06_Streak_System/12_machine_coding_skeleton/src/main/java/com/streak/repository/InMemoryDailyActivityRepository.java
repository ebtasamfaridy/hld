package com.streak.repository;

import com.streak.domain.DailyActivity;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDailyActivityRepository implements DailyActivityRepository {

    private record Key(UserId u, StreakType t, LocalDate d) {}
    private final Map<Key, DailyActivity> store = new ConcurrentHashMap<>();

    @Override
    public void incrementOrCreate(UserId userId, StreakType type, LocalDate day,
                                  Instant eventAt, ZoneId tz) {
        Key k = new Key(userId, type, day);
        store.compute(k, (kk, existing) -> {
            if (existing == null) {
                return new DailyActivity(userId, type, day, 1, eventAt, eventAt, tz);
            }
            return existing.incrementWith(eventAt);
        });
    }

    @Override
    public List<DailyActivity> findInRange(UserId u, StreakType t, LocalDate from, LocalDate to) {
        List<DailyActivity> out = new ArrayList<>();
        for (var e : store.entrySet()) {
            Key k = e.getKey();
            if (!k.u.equals(u) || k.t != t) continue;
            if (k.d.isBefore(from) || k.d.isAfter(to)) continue;
            out.add(e.getValue());
        }
        out.sort(Comparator.comparing(DailyActivity::day));
        return out;
    }
}
