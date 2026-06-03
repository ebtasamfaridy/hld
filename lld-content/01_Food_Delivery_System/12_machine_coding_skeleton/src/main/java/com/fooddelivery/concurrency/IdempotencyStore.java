package com.fooddelivery.concurrency;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Atomic-put-if-absent store mapping idempotency-key -> resource id.
 * Used by services that need at-most-once mutation semantics.
 */
public final class IdempotencyStore {
    private final ConcurrentMap<String, UUID> store = new ConcurrentHashMap<>();

    public Optional<UUID> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    /**
     * @return true if this call wrote the entry; false if it already existed.
     */
    public boolean put(String key, UUID resourceId) {
        return store.putIfAbsent(key, resourceId) == null;
    }
}
