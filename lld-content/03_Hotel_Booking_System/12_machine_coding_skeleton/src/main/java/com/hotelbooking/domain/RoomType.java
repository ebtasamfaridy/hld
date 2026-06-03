package com.hotelbooking.domain;

import java.util.UUID;

public final class RoomType {
    private final UUID id;
    private final UUID hotelId;
    private final String name;
    private final int maxOccupancy;

    public RoomType(UUID hotelId, String name, int maxOccupancy) {
        this.id = UUID.randomUUID();
        this.hotelId = hotelId;
        this.name = name;
        this.maxOccupancy = maxOccupancy;
    }
    public UUID id() { return id; }
    public UUID hotelId() { return hotelId; }
    public String name() { return name; }
    public int maxOccupancy() { return maxOccupancy; }
}
