package com.carrental.store;

import com.carrental.trip.Trip;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TripRepository {
    private final ConcurrentMap<UUID, Trip> byId = new ConcurrentHashMap<>();
    public Trip save(Trip t) { byId.put(t.id(), t); return t; }
    public Optional<Trip> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
}
