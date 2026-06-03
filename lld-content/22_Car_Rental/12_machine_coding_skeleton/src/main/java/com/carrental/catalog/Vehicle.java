package com.carrental.catalog;

import com.carrental.domain.Enums.VehicleStatus;
import com.carrental.domain.GeoPoint;
import com.carrental.domain.Ids;

import java.util.UUID;

public final class Vehicle {
    private final UUID id;
    private final UUID modelId;
    private final String plate;
    private volatile GeoPoint location;
    private volatile int lastFuelPct;
    private volatile int lastOdoKm;
    private volatile VehicleStatus status = VehicleStatus.ACTIVE;

    public Vehicle(UUID modelId, String plate, GeoPoint location, int initialFuelPct, int initialOdoKm) {
        this.id = Ids.newId();
        this.modelId = modelId;
        this.plate = plate;
        this.location = location;
        this.lastFuelPct = initialFuelPct;
        this.lastOdoKm = initialOdoKm;
    }

    public UUID id() { return id; }
    public UUID modelId() { return modelId; }
    public String plate() { return plate; }
    public GeoPoint location() { return location; }
    public int lastFuelPct() { return lastFuelPct; }
    public int lastOdoKm() { return lastOdoKm; }
    public VehicleStatus status() { return status; }

    public boolean isReservable() { return status == VehicleStatus.ACTIVE; }

    public void updateState(GeoPoint loc, int fuelPct, int odoKm) {
        this.location = loc;
        this.lastFuelPct = fuelPct;
        this.lastOdoKm = odoKm;
    }

    public void setStatus(VehicleStatus s) { this.status = s; }

    @Override public String toString() {
        return plate + " (fuel=" + lastFuelPct + "%, odo=" + lastOdoKm + " km, status=" + status + ")";
    }
}
