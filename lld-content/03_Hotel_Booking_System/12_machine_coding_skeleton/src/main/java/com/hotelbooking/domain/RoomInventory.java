package com.hotelbooking.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per (hotel, room_type, date). Mutated atomically by the repository's
 * decrement / increment methods (which simulate SQL CAS).
 */
public final class RoomInventory {
    private final UUID hotelId;
    private final UUID roomTypeId;
    private final LocalDate date;
    private final int totalRooms;
    private int availableRooms;
    private Money basePrice;
    private boolean blocked;

    public RoomInventory(UUID hotelId, UUID roomTypeId, LocalDate date,
                         int totalRooms, Money basePrice) {
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.date = date;
        this.totalRooms = totalRooms;
        this.availableRooms = totalRooms;
        this.basePrice = basePrice;
    }

    public UUID hotelId() { return hotelId; }
    public UUID roomTypeId() { return roomTypeId; }
    public LocalDate date() { return date; }
    public int totalRooms() { return totalRooms; }
    public int availableRooms() { return availableRooms; }
    public Money basePrice() { return basePrice; }
    public boolean blocked() { return blocked; }

    /** Internal: mutate via repository. */
    void decrementUnsafe(int n) { availableRooms -= n; }
    void incrementUnsafe(int n) { availableRooms = Math.min(totalRooms, availableRooms + n); }
    public void block() { this.blocked = true; }
    public void unblock() { this.blocked = false; }
    public void setPrice(Money p) { this.basePrice = p; }

    @Override public String toString() {
        return "Inv{" + date + " avail=" + availableRooms + "/" + totalRooms
             + (blocked ? " BLOCKED" : "") + " @" + basePrice + "}";
    }
}
