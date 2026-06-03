package com.carrental.store;

import com.carrental.reservation.Reservation;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReservationRepository {
    private final ConcurrentMap<UUID, Reservation> byId = new ConcurrentHashMap<>();
    public Reservation save(Reservation r) { byId.put(r.id(), r); return r; }
    public Optional<Reservation> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
}
