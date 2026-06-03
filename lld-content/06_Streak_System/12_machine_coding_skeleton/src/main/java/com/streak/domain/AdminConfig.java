package com.streak.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class AdminConfig {
    private final StreakType activeStreakType;
    private final List<Integer> milestones;
    private final int listeningMinSeconds;
    private final long version;
    private final Instant updatedAt;

    public AdminConfig(StreakType activeStreakType, List<Integer> milestones,
                       int listeningMinSeconds, long version, Instant updatedAt) {
        this.activeStreakType = Objects.requireNonNull(activeStreakType);
        this.milestones = List.copyOf(milestones);
        this.listeningMinSeconds = listeningMinSeconds;
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AdminConfig defaults() {
        return new AdminConfig(
                StreakType.APP_VISIT,
                List.of(7, 30, 100, 365),
                30,
                0,
                Instant.EPOCH);
    }

    public AdminConfig withActiveType(StreakType t, Instant now) {
        if (t == activeStreakType) return this;
        return new AdminConfig(t, milestones, listeningMinSeconds, version + 1, now);
    }

    public StreakType activeStreakType() { return activeStreakType; }
    public List<Integer> milestones()    { return milestones; }
    public int listeningMinSeconds()     { return listeningMinSeconds; }
    public long version()                { return version; }
    public Instant updatedAt()           { return updatedAt; }
}
