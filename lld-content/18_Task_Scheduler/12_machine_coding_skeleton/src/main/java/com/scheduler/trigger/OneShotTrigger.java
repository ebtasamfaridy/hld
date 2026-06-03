package com.scheduler.trigger;

import java.time.Instant;
import java.util.Optional;

public final class OneShotTrigger implements Trigger {
    private final Instant fireAt;
    public OneShotTrigger(Instant fireAt) { this.fireAt = fireAt; }

    @Override
    public Optional<Instant> nextFireTime(Instant previous, Instant now) {
        return previous == null ? Optional.of(fireAt) : Optional.empty();
    }
    @Override public String describe() { return "OneShot@" + fireAt; }
}
