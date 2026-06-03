package com.ridebooking.domain;

import java.util.UUID;

public final class Driver {
    private final UUID id;
    private final String name;
    private final String city;
    private final Vehicle vehicle;
    private DriverStatus status;
    private Location lastLocation;
    private double rating;
    private long version;

    public Driver(UUID id, String name, String city, Vehicle vehicle, Location startAt, double rating) {
        this.id = id; this.name = name; this.city = city;
        this.vehicle = vehicle; this.lastLocation = startAt; this.rating = rating;
        this.status = DriverStatus.OFFLINE;
    }

    public void goOnline()  { require(DriverStatus.OFFLINE); status = DriverStatus.IDLE; version++; }
    public void goOffline() { if (status == DriverStatus.IN_TRIP) throw new IllegalStateException(); status = DriverStatus.OFFLINE; version++; }
    public void reserve()   { require(DriverStatus.IDLE); status = DriverStatus.OFFER_PENDING; version++; }
    public void release()   { require(DriverStatus.OFFER_PENDING); status = DriverStatus.IDLE; version++; }
    public void enRoute()   { require(DriverStatus.OFFER_PENDING); status = DriverStatus.EN_ROUTE_PICKUP; version++; }
    public void atPickup()  { require(DriverStatus.EN_ROUTE_PICKUP); status = DriverStatus.AT_PICKUP; version++; }
    public void startTrip() { require(DriverStatus.AT_PICKUP); status = DriverStatus.IN_TRIP; version++; }
    public void endTrip()   { require(DriverStatus.IN_TRIP); status = DriverStatus.IDLE; version++; }
    public void backToIdle() {
        // For ride cancellation while driver was EN_ROUTE_PICKUP / AT_PICKUP
        if (status == DriverStatus.EN_ROUTE_PICKUP || status == DriverStatus.AT_PICKUP) {
            status = DriverStatus.IDLE; version++;
        }
    }

    public void updateLocation(Location l) { this.lastLocation = l; }

    private void require(DriverStatus s) {
        if (status != s) throw new IllegalStateException("driver " + id + " in " + status + " not " + s);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String city() { return city; }
    public Vehicle vehicle() { return vehicle; }
    public DriverStatus status() { return status; }
    public Location lastLocation() { return lastLocation; }
    public double rating() { return rating; }
    public long version() { return version; }

    @Override public String toString() {
        return "Driver{" + name + " " + status + " v=" + version + "}";
    }
}
