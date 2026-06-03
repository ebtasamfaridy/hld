package com.hotelbooking.service;

import com.hotelbooking.repository.RoomInventoryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InventoryService {
    private final RoomInventoryRepository repo;

    public InventoryService(RoomInventoryRepository repo) { this.repo = repo; }

    /**
     * Atomically reserve roomCount rooms for each night in [checkIn, checkOut).
     * If any night fails, releases what was already reserved and returns false.
     */
    public boolean reserveRange(UUID hotelId, UUID roomTypeId,
                                LocalDate checkIn, LocalDate checkOut, int roomCount) {
        List<LocalDate> reserved = new ArrayList<>();
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            if (repo.decrement(hotelId, roomTypeId, d, roomCount)) {
                reserved.add(d);
            } else {
                // rollback
                for (LocalDate r : reserved) repo.increment(hotelId, roomTypeId, r, roomCount);
                return false;
            }
        }
        return true;
    }

    public void releaseRange(UUID hotelId, UUID roomTypeId,
                             LocalDate checkIn, LocalDate checkOut, int roomCount) {
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            repo.increment(hotelId, roomTypeId, d, roomCount);
        }
    }
}
