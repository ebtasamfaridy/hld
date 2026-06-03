package com.scheduler.trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class FixedDelayTrigger implements Trigger {
    private final Duration delay;
    private final Instant firstFireAt;

    public FixedDelayTrigger(Duration delay, Instant firstFireAt) {
        this.delay = delay;
        this.firstFireAt = firstFireAt;
    }

    @Override
    public Optional<Instant> nextFireTime(Instant previous, Instant now) {
        if (previous == null) return Optional.of(firstFireAt);
        // Caller passes "execution finish" as 'previous' for fixed-delay semantics.
        return Optional.of(previous.plus(delay));
    }
    @Override public String describe() { return "FixedDelay@" + delay; }
}
