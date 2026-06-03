package com.streak.service;

import com.streak.cache.StreakCache;
import com.streak.domain.AdminConfig;
import com.streak.domain.StreakType;
import com.streak.repository.AdminConfigRepository;

import java.time.Clock;

public final class AdminService {

    private final AdminConfigRepository repo;
    private final StreakCache cache;
    private final EventBus bus;
    private final Clock clock;

    public AdminService(AdminConfigRepository repo, StreakCache cache,
                        EventBus bus, Clock clock) {
        this.repo = repo;
        this.cache = cache;
        this.bus = bus;
        this.clock = clock;
    }

    public AdminConfig getConfig() { return repo.get(); }

    public AdminConfig setActiveType(StreakType newType, long expectedVersion) {
        AdminConfig current = repo.get();
        if (current.version() != expectedVersion) {
            throw new IllegalStateException(
                    "version conflict: current=" + current.version()
                            + " expected=" + expectedVersion);
        }
        StreakType oldType = current.activeStreakType();
        AdminConfig updated = current.withActiveType(newType, clock.instant());
        if (updated == current) return current;   // same value: no-op

        if (!repo.saveWithCas(updated)) {
            throw new IllegalStateException("CAS failed (someone else updated)");
        }
        cache.setActiveType(newType);
        cache.invalidateAllStreaks();
        bus.publish(new DomainEvent.AdminConfigChanged(oldType, newType, clock.instant()));
        return updated;
    }
}
