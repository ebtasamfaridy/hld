package com.ridebooking.repository;

import com.ridebooking.domain.Ride;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RideRepository {
    private final ConcurrentMap<UUID, Ride> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> byKey = new ConcurrentHashMap<>();

    public Optional<Ride> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

    public Optional<Ride> findByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        UUID id = byKey.get(key);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    public Ride save(Ride r) {
        if (r.idempotencyKey() != null) {
            UUID prev = byKey.putIfAbsent(r.idempotencyKey(), r.id());
            if (prev != null && !prev.equals(r.id())) {
                return byId.get(prev);
            }
        }
        byId.put(r.id(), r);
        return r;
    }
}
