package com.scheduler.trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class FixedRateTrigger implements Trigger {
    private final Instant startAt;
    private final Duration period;

    public FixedRateTrigger(Instant startAt, Duration period) {
        this.startAt = startAt;
        this.period = period;
    }

    @Override
    public Optional<Instant> nextFireTime(Instant previous, Instant now) {
        if (previous == null) return Optional.of(startAt);
        Instant next = previous.plus(period);
        // If we're way behind (downtime), jump ahead to "now" boundary.
        if (next.isBefore(now)) {
            long missed = Duration.between(next, now).toNanos() / period.toNanos();
            next = next.plus(period.multipliedBy(missed));
        }
        return Optional.of(next);
    }
    @Override public String describe() { return "FixedRate@" + period; }
}
