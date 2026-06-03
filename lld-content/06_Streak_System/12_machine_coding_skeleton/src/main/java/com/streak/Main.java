package com.streak;

import com.streak.cache.InMemoryStreakCache;
import com.streak.cache.StreakCache;
import com.streak.classifier.AppVisitClassifier;
import com.streak.classifier.CompositeClassifier;
import com.streak.classifier.ListeningClassifier;
import com.streak.domain.*;
import com.streak.repository.*;
import com.streak.service.*;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composition root + demo runner.
 *
 * Demonstrates:
 *   - dedup (second event same day is NoOp)
 *   - streak advancement
 *   - streak break + restart
 *   - parallel tracking of APP_VISIT and LISTENING
 *   - milestone firing (idempotent)
 *   - admin switch invalidates cache
 *   - calendar view with gaps
 */
public final class Main {

    public static void main(String[] args) {
        // Use a mutable clock so we can fast-forward across days.
        var clock = new MutableClock(Instant.parse("2025-06-01T05:00:00Z"));

        StreakStateRepository       stateRepo   = new InMemoryStreakStateRepository();
        DailyActivityRepository     activityRepo = new InMemoryDailyActivityRepository();
        AdminConfigRepository       configRepo  = new InMemoryAdminConfigRepository();
        MilestoneAwardRepository    awardRepo   = new InMemoryMilestoneAwardRepository();
        StreakCache                 cache       = new InMemoryStreakCache();
        EventBus                    bus         = new InMemoryEventBus();

        var classifier = new CompositeClassifier(List.of(
                new AppVisitClassifier(),
                new ListeningClassifier(30)
        ));

        var streakSvc = new StreakService(stateRepo, activityRepo, classifier, cache, bus, clock);
        var adminSvc  = new AdminService(configRepo, cache, bus, clock);

        var milestones = new MilestoneListener(configRepo, awardRepo, bus, clock);
        milestones.register(bus);

        // Console listener — prints all events
        bus.subscribe(DomainEvent.StreakAdvanced.class,
                e -> log("ADVANCED  " + e.userId() + " " + e.type()
                        + " prev=" + e.previousCurrent() + " current=" + e.currentStreak()));
        bus.subscribe(DomainEvent.StreakBroken.class,
                e -> log("BROKEN    " + e.userId() + " " + e.type()
                        + " was=" + e.previousCurrent() + " best=" + e.previousLongest()));
        bus.subscribe(DomainEvent.StreakMilestoneReached.class,
                e -> log("MILESTONE " + e.userId() + " " + e.type() + " " + e.milestoneDays() + "d"));
        bus.subscribe(DomainEvent.AdminConfigChanged.class,
                e -> log("ADMIN     " + e.oldType() + " -> " + e.newType()));

        UserId alice = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000a11c"));
        UserId bob   = new UserId(UUID.fromString("00000000-0000-0000-0000-00000000b0b1"));
        ZoneId tz = ZoneId.of("Asia/Kolkata");

        // ── Day 1 ─────────────────────────────────────────────────────────────
        section("Day 1");
        streakSvc.recordActivity(sessionEvent(alice, tz, clock.instant()));
        streakSvc.recordActivity(sessionEvent(alice, tz, clock.instant().plusSeconds(60))); // dedup NoOp
        streakSvc.recordActivity(sessionEvent(bob,   tz, clock.instant()));

        // ── Day 2 ─────────────────────────────────────────────────────────────
        clock.advance(Duration.ofDays(1));
        section("Day 2");
        streakSvc.recordActivity(sessionEvent(alice, tz, clock.instant()));
        streakSvc.recordActivity(playEvent(bob, tz, clock.instant(), 60)); // listening +1

        // ── Day 3 — Alice skips ──────────────────────────────────────────────
        clock.advance(Duration.ofDays(1));
        section("Day 3 (Alice skips)");
        streakSvc.recordActivity(playEvent(bob, tz, clock.instant(), 90));

        // ── Day 4 — Alice returns; her APP_VISIT streak restarts ────────────
        clock.advance(Duration.ofDays(1));
        section("Day 4 (Alice returns; expect BROKEN + ADVANCED=1)");
        streakSvc.recordActivity(sessionEvent(alice, tz, clock.instant()));
        streakSvc.recordActivity(playEvent(bob, tz, clock.instant(), 120));

        // ── Days 5–10: Bob keeps listening; expect 7-day milestone ────────────
        for (int i = 5; i <= 10; i++) {
            clock.advance(Duration.ofDays(1));
            section("Day " + i + " (Bob listens)");
            streakSvc.recordActivity(playEvent(bob, tz, clock.instant(), 60));
        }

        // Snapshots
        section("Snapshots after Day 10");
        log(streakSvc.getStreak(alice, StreakType.APP_VISIT).toString());
        log(streakSvc.getStreak(bob,   StreakType.APP_VISIT).toString());
        log(streakSvc.getStreak(bob,   StreakType.LISTENING).toString());

        // Admin switches active to LISTENING
        section("Admin switches active type to LISTENING");
        var cfg = adminSvc.getConfig();
        adminSvc.setActiveType(StreakType.LISTENING, cfg.version());
        log("active type now: " + cache.getActiveType());

        // Calendar for Bob (LISTENING)
        section("Bob LISTENING calendar for 2025-06");
        for (CalendarDay d : streakSvc.getCalendar(bob, StreakType.LISTENING, YearMonth.of(2025, 6))) {
            if (d.active()) log("  " + d.day() + "  ✓ (count=" + d.eventCount() + ")");
        }
    }

    // ---------- helpers ----------

    private static RawAppEvent sessionEvent(UserId u, ZoneId tz, Instant at) {
        return new RawAppEvent(
                "evt-" + UUID.randomUUID(), u, RawAppEvent.Kind.SESSION_STARTED,
                Map.of(), at, tz);
    }

    private static RawAppEvent playEvent(UserId u, ZoneId tz, Instant at, int seconds) {
        return new RawAppEvent(
                "evt-" + UUID.randomUUID(), u, RawAppEvent.Kind.EPISODE_PLAYED,
                Map.of("duration_seconds", seconds), at, tz);
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
    private static void log(String s)    { System.out.println("  " + s); }

    /** Mutable clock for the demo; production uses Clock.systemUTC() or injected. */
    static final class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone = ZoneOffset.UTC;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }
}
