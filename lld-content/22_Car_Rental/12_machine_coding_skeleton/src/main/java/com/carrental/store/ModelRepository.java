package com.carrental.store;

import com.carrental.catalog.VehicleModel;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ModelRepository {
    private final ConcurrentMap<UUID, VehicleModel> byId = new ConcurrentHashMap<>();
    public VehicleModel save(VehicleModel m) { byId.put(m.id(), m); return m; }
    public Optional<VehicleModel> byId(UUID id) { return Optional.ofNullable(byId.get(id)); }
}
