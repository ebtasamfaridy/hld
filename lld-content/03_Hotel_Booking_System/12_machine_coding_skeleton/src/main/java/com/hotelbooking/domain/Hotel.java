package com.hotelbooking.domain;

import java.util.UUID;

public final class Hotel {
    private final UUID id;
    private final String name;
    private final String city;

    public Hotel(String name, String city) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.city = city;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String city() { return city; }
}
