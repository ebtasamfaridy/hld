package com.streak.repository;

import com.streak.domain.MilestoneAward;
import com.streak.domain.StreakType;
import com.streak.domain.UserId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMilestoneAwardRepository implements MilestoneAwardRepository {

    private record Key(UserId u, StreakType t, int days) {}

    private final Set<Key> keys = ConcurrentHashMap.newKeySet();
    private final List<MilestoneAward> awards = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean saveIfAbsent(MilestoneAward a) {
        Key k = new Key(a.userId(), a.streakType(), a.milestoneDays());
        if (!keys.add(k)) return false;
        awards.add(a);
        return true;
    }

    @Override
    public List<MilestoneAward> findByUser(UserId u) {
        List<MilestoneAward> out = new ArrayList<>();
        synchronized (awards) {
            for (MilestoneAward a : awards) if (a.userId().equals(u)) out.add(a);
        }
        return out;
    }

    @Override
    public boolean exists(UserId u, StreakType t, int days) {
        return keys.contains(new Key(u, t, days));
    }
}
