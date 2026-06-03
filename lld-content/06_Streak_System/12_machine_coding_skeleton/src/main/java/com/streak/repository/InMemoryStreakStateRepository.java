package com.streak.repository;

import com.streak.domain.StreakState;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStreakStateRepository implements StreakStateRepository {

    private record Key(UserId u, StreakType t) {}
    private final Map<Key, StreakState> store = new ConcurrentHashMap<>();

    @Override
    public Optional<StreakState> findByUserAndType(UserId u, StreakType t) {
        return Optional.ofNullable(store.get(new Key(u, t)));
    }

    @Override
    public boolean saveWithCas(StreakState updated) {
        Key k = new Key(updated.userId(), updated.streakType());
        long expectedPrev = updated.version() - 1;
        if (expectedPrev == 0) {
            return store.putIfAbsent(k, updated) == null;
        }
        StreakState[] swapped = new StreakState[1];
        store.compute(k, (kk, existing) -> {
            if (existing == null) return null;
            if (existing.version() != expectedPrev) return existing;
            swapped[0] = updated;
            return updated;
        });
        return swapped[0] != null;
    }
}
