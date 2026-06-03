package com.carrental.store;

import com.carrental.catalog.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class VehicleRepository {
    private final ConcurrentMap<UUID, Vehicle> byId = new ConcurrentHashMap<>();
    public Vehicle save(Vehicle v) { byId.put(v.id(), v); return v; }
    public Optional<Vehicle> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public List<Vehicle> all() { return List.copyOf(byId.values()); }
}
