package com.fooddelivery.domain;

import java.util.UUID;

public final class MenuItem {
    private final UUID id;
    private final UUID restaurantId;
    private final String name;
    private final Money price;
    private boolean available;

    public MenuItem(UUID restaurantId, String name, Money price) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.name = name;
        this.price = price;
        this.available = true;
    }

    public UUID id() { return id; }
    public UUID restaurantId() { return restaurantId; }
    public String name() { return name; }
    public Money price() { return price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean a) { this.available = a; }
}
