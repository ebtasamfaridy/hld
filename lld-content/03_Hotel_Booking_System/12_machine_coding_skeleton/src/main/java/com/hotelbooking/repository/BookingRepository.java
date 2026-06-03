package com.hotelbooking.repository;

import com.hotelbooking.domain.Booking;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BookingRepository {
    private final ConcurrentMap<UUID, Booking> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> byKey = new ConcurrentHashMap<>();

    public Optional<Booking> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

    public Optional<Booking> findByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        UUID id = byKey.get(key);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Booking save(Booking b) {
        if (b.idempotencyKey() != null) {
            UUID prev = byKey.putIfAbsent(b.idempotencyKey(), b.id());
            if (prev != null && !prev.equals(b.id())) return byId.get(prev);
        }
        byId.put(b.id(), b);
        return b;
    }
}
