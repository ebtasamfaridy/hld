package com.streak.service;

import com.streak.domain.MilestoneAward;
import com.streak.repository.AdminConfigRepository;
import com.streak.repository.MilestoneAwardRepository;

import java.time.Clock;

/**
 * Subscribes to StreakAdvanced events. For each milestone threshold crossed
 * by the (previousCurrent, currentStreak] interval, attempts to insert
 * a MilestoneAward; only on insert does it publish StreakMilestoneReached.
 *
 * The (a, b] interval handling is robust to unusual jumps (Backfill repair
 * scripts, etc.) — the current-streak normally only changes by ±1.
 */
public final class MilestoneListener {

    private final AdminConfigRepository configRepo;
    private final MilestoneAwardRepository awardRepo;
    private final EventBus bus;
    private final Clock clock;

    public MilestoneListener(AdminConfigRepository configRepo,
                             MilestoneAwardRepository awardRepo,
                             EventBus bus,
                             Clock clock) {
        this.configRepo = configRepo;
        this.awardRepo = awardRepo;
        this.bus = bus;
        this.clock = clock;
    }

    public void register(EventBus bus) {
        bus.subscribe(DomainEvent.StreakAdvanced.class, this::onAdvanced);
    }

    private void onAdvanced(DomainEvent.StreakAdvanced e) {
        var milestones = configRepo.get().milestones();
        for (int days : milestones) {
            if (days <= e.previousCurrent()) continue;
            if (days > e.currentStreak()) continue;
            boolean inserted = awardRepo.saveIfAbsent(new MilestoneAward(
                    e.userId(), e.type(), days, clock.instant()));
            if (inserted) {
                bus.publish(new DomainEvent.StreakMilestoneReached(
                        e.userId(), e.type(), days, clock.instant()));
            }
        }
    }
}
