package com.streak.cache;

import com.streak.domain.StreakState;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryStreakCache implements StreakCache {

    private record DedupKey(UserId u, StreakType t, LocalDate d, long ver) {}

    private final Set<DedupKey> dedup = ConcurrentHashMap.newKeySet();
    private final Map<String, StreakState> streaks = new ConcurrentHashMap<>();

    private final AtomicLong cacheVersion = new AtomicLong(1);
    private final AtomicReference<StreakType> activeType =
            new AtomicReference<>(StreakType.APP_VISIT);

    @Override
    public boolean tryDedup(UserId u, StreakType t, LocalDate d) {
        return dedup.add(new DedupKey(u, t, d, cacheVersion.get()));
    }

    @Override
    public Optional<StreakState> getStreak(UserId u, StreakType t) {
        return Optional.ofNullable(streaks.get(key(u, t)));
    }

    @Override
    public void putStreak(StreakState s) {
        streaks.put(key(s.userId(), s.streakType()), s);
    }

    @Override
    public void invalidateAllStreaks() {
        cacheVersion.incrementAndGet();
        streaks.clear();   // simplification; production uses prefix versioning
    }

    @Override public StreakType getActiveType() { return activeType.get(); }

    @Override public void setActiveType(StreakType t) { activeType.set(t); }

    private String key(UserId u, StreakType t) {
        return "v" + cacheVersion.get() + ":" + u + ":" + t;
    }
}
