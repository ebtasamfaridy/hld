package com.ratelimit.store;

import java.util.function.Function;

/**
 * Simple key-value store with atomic compute-then-write per key.
 * The compute function MUST be deterministic / side-effect-free; it may run
 * multiple times in case of contention.
 */
public interface Store<S> {
    /**
     * Atomically read-modify-write state for {@code key}.
     * The function returns a new state given the current state (or null if absent).
     */
    S compute(String key, Function<S, S> updater);

    S getOrNull(String key);
}
