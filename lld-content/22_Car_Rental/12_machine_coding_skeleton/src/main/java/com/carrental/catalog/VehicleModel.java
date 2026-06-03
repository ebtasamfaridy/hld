package com.carrental.catalog;

import com.carrental.domain.Ids;
import com.carrental.domain.Money;

import java.util.UUID;

public final class VehicleModel {
    private final UUID id;
    private final String name;
    private final int seats;
    private final int fuelTankLitres;
    private final Money hourlyRate;
    private final Money perKmRate;

    public VehicleModel(String name, int seats, int fuelTankLitres,
                        Money hourlyRate, Money perKmRate) {
        this.id = Ids.newId();
        this.name = name;
        this.seats = seats;
        this.fuelTankLitres = fuelTankLitres;
        this.hourlyRate = hourlyRate;
        this.perKmRate = perKmRate;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public int seats() { return seats; }
    public int fuelTankLitres() { return fuelTankLitres; }
    public Money hourlyRate() { return hourlyRate; }
    public Money perKmRate() { return perKmRate; }

    @Override public String toString() {
        return name + " (" + seats + "-seat, " + hourlyRate + "/hr, " + perKmRate + "/km)";
    }
}
