package com.fooddelivery.domain;

import java.util.UUID;

public final class Driver {
    private final UUID id;
    private final String name;
    private final String city;
    private DriverStatus status;
    private Location lastLocation;
    private double rating;
    private long version;

    public Driver(UUID id, String name, String city, Location startLocation, double rating) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.lastLocation = startLocation;
        this.rating = rating;
        this.status = DriverStatus.OFFLINE;
        this.version = 0;
    }

    public void goOnline() {
        if (status != DriverStatus.OFFLINE)
            throw new IllegalStateException("driver " + id + " not offline");
        status = DriverStatus.IDLE;
        version++;
    }

    public void goOffline() {
        if (status == DriverStatus.BUSY)
            throw new IllegalStateException("cannot go offline while BUSY");
        status = DriverStatus.OFFLINE;
        version++;
    }

    public void reserveForOffer() {
        if (status != DriverStatus.IDLE)
            throw new IllegalStateException("driver " + id + " not idle");
        status = DriverStatus.OFFER_PENDING;
        version++;
    }

    public void releaseFromOffer() {
        if (status != DriverStatus.OFFER_PENDING)
            throw new IllegalStateException("driver " + id + " not OFFER_PENDING");
        status = DriverStatus.IDLE;
        version++;
    }

    public void markBusy() {
        if (status != DriverStatus.OFFER_PENDING)
            throw new IllegalStateException("driver " + id + " not OFFER_PENDING");
        status = DriverStatus.BUSY;
        version++;
    }

    public void markIdle() {
        if (status != DriverStatus.BUSY)
            throw new IllegalStateException("driver " + id + " not BUSY");
        status = DriverStatus.IDLE;
        version++;
    }

    public void updateLocation(Location loc) {
        this.lastLocation = loc;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String city() { return city; }
    public DriverStatus status() { return status; }
    public Location lastLocation() { return lastLocation; }
    public double rating() { return rating; }
    public long version() { return version; }

    @Override public String toString() {
        return "Driver{id=" + id + ", name=" + name + ", status=" + status
             + ", at=" + lastLocation + ", v=" + version + "}";
    }
}
