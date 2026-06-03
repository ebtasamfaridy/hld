package com.library.repository;

import com.library.domain.Reservation;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReservationRepository {
    private final ConcurrentMap<UUID, Reservation> byId = new ConcurrentHashMap<>();

    public Reservation save(Reservation r) { byId.put(r.id(), r); return r; }
    public Optional<Reservation> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

    public synchronized int nextQueuePosition(UUID bookId) {
        int max = byId.values().stream()
                .filter(r -> r.bookId().equals(bookId)
                          && (r.status() == Reservation.Status.QUEUED
                           || r.status() == Reservation.Status.READY))
                .mapToInt(Reservation::queuePosition).max().orElse(0);
        return max + 1;
    }

    public synchronized Optional<Reservation> findHeadOfQueue(UUID bookId) {
        return byId.values().stream()
                .filter(r -> r.bookId().equals(bookId) && r.status() == Reservation.Status.QUEUED)
                .min(Comparator.comparingInt(Reservation::queuePosition));
    }
}
