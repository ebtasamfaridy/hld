package com.ridebooking.domain;

public final class Vehicle {
    private final String plate;
    private final String make;
    private final String model;
    private final RideType supports;

    public Vehicle(String plate, String make, String model, RideType supports) {
        this.plate = plate; this.make = make; this.model = model; this.supports = supports;
    }
    public String plate() { return plate; }
    public String make() { return make; }
    public String model() { return model; }
    public RideType supports() { return supports; }
    @Override public String toString() { return make + " " + model + " (" + plate + ")"; }
}
