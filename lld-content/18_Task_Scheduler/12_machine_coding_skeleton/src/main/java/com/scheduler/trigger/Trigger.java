package com.scheduler.trigger;

import java.time.Instant;
import java.util.Optional;

public interface Trigger {
    /**
     * Given the previous fire time (null on first call), returns the next, or empty if exhausted.
     */
    Optional<Instant> nextFireTime(Instant previousFireTime, Instant now);

    String describe();
}
