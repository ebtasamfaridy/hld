package com.parking.domain;

public record Vehicle(String plate, VehicleType type, boolean handicapPermit) {
    public static Vehicle of(String plate, VehicleType t) { return new Vehicle(plate, t, false); }
}
