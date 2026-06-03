package com.fooddelivery.domain;

import java.util.UUID;

public final class Restaurant {
    private final UUID id;
    private final String name;
    private final String city;
    private final Location location;
    private boolean active;

    public Restaurant(String name, String city, Location location) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.city = city;
        this.location = location;
        this.active = true;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String city() { return city; }
    public Location location() { return location; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
}
