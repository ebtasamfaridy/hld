package com.carrental.catalog;

import com.carrental.store.ModelRepository;
import com.carrental.store.VehicleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CatalogService {
    private final ModelRepository models;
    private final VehicleRepository vehicles;

    public CatalogService(ModelRepository models, VehicleRepository vehicles) {
        this.models = models;
        this.vehicles = vehicles;
    }

    public VehicleModel addModel(VehicleModel m) { return models.save(m); }
    public Vehicle addVehicle(Vehicle v) { return vehicles.save(v); }

    public Vehicle requireVehicle(UUID id) {
        return vehicles.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("vehicle not found: " + id));
    }
    public VehicleModel requireModel(UUID id) {
        return models.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("model not found: " + id));
    }
    public Optional<Vehicle> findVehicle(UUID id) { return vehicles.byId(id); }
    public List<Vehicle> all() { return vehicles.all(); }
}
