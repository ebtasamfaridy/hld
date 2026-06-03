package com.streak.service;

import com.streak.cache.StreakCache;
import com.streak.classifier.CompositeClassifier;
import com.streak.domain.*;
import com.streak.repository.DailyActivityRepository;
import com.streak.repository.StreakStateRepository;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Core streak application service:
 *  - recordActivity: hot path (dedup → upsert daily → CAS streak → publish)
 *  - getStreak:     cache-first read; computes is_alive on read
 *  - getCalendar:   range scan + gap filling
 */
public final class StreakService {

    private static final int CAS_MAX_RETRIES = 3;

    private final StreakStateRepository stateRepo;
    private final DailyActivityRepository activityRepo;
    private final CompositeClassifier classifier;
    private final StreakCache cache;
    private final EventBus bus;
    private final Clock clock;

    public StreakService(StreakStateRepository stateRepo,
                         DailyActivityRepository activityRepo,
                         CompositeClassifier classifier,
                         StreakCache cache,
                         EventBus bus,
                         Clock clock) {
        this.stateRepo = stateRepo;
        this.activityRepo = activityRepo;
        this.classifier = classifier;
        this.cache = cache;
        this.bus = bus;
        this.clock = clock;
    }

    public void recordActivity(RawAppEvent raw) {
        Instant now = clock.instant();
        for (ClassifiedEvent ce : classifier.classifyAll(raw)) {
            applyClassified(ce, now);
        }
    }

    private void applyClassified(ClassifiedEvent ce, Instant now) {
        LocalDate day = ce.eventDay();
        boolean firstOfDay = cache.tryDedup(ce.userId(), ce.type(), day);

        activityRepo.incrementOrCreate(ce.userId(), ce.type(), day,
                ce.occurredAt(), ce.userTimezone());

        bus.publish(new DomainEvent.ActivityRecorded(
                ce.userId(), ce.type(), day, firstOfDay, now));

        if (!firstOfDay) return;

        StreakUpdate update = applyWithCas(ce, now);

        if (update instanceof StreakUpdate.Advanced a) {
            cache.putStreak(a.state());
            bus.publish(new DomainEvent.StreakAdvanced(
                    ce.userId(), ce.type(), a.previousCurrent(), a.newCurrent(),
                    a.state().longest(), now));
        } else if (update instanceof StreakUpdate.Restarted r) {
            cache.putStreak(r.state());
            bus.publish(new DomainEvent.StreakBroken(
                    ce.userId(), ce.type(), r.previousCurrent(), r.previousLongest(), now));
            bus.publish(new DomainEvent.StreakAdvanced(
                    ce.userId(), ce.type(), 0, 1, r.state().longest(), now));
        }
        // NoOp / Backfilled: no streak_state mutation
    }

    private StreakUpdate applyWithCas(ClassifiedEvent ce, Instant now) {
        for (int attempt = 0; attempt < CAS_MAX_RETRIES; attempt++) {
            StreakState state = stateRepo.findByUserAndType(ce.userId(), ce.type())
                    .orElseGet(() -> StreakState.empty(ce.userId(), ce.type(), ce.userTimezone()));

            StreakUpdate update = state.recordActivity(ce.eventDay(), ce.userTimezone(), now);

            if (update instanceof StreakUpdate.NoOp || update instanceof StreakUpdate.Backfilled) {
                return update;
            }
            if (stateRepo.saveWithCas(update.state())) {
                return update;
            }
            // CAS lost; loop and retry
        }
        throw new IllegalStateException(
                "max CAS retries exceeded for user=" + ce.userId() + " type=" + ce.type());
    }

    public StreakSnapshot getStreak(UserId userId, StreakType type) {
        StreakState state = cache.getStreak(userId, type)
                .or(() -> {
                    var fromDb = stateRepo.findByUserAndType(userId, type);
                    fromDb.ifPresent(cache::putStreak);
                    return fromDb;
                })
                .orElseGet(() -> StreakState.empty(userId, type, ZoneId.of("UTC")));
        LocalDate today = LocalDate.now(state.userTimezone() == null ? ZoneId.of("UTC") : state.userTimezone());
        return StreakSnapshot.from(state, today);
    }

    public List<CalendarDay> getCalendar(UserId userId, StreakType type, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();
        List<DailyActivity> rows = activityRepo.findInRange(userId, type, from, to);

        List<CalendarDay> out = new ArrayList<>(month.lengthOfMonth());
        int idx = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (idx < rows.size() && rows.get(idx).day().equals(d)) {
                out.add(CalendarDay.active(d, rows.get(idx).eventCount()));
                idx++;
            } else {
                out.add(CalendarDay.inactive(d));
            }
        }
        return out;
    }
}
