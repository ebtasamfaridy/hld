package com.hotelbooking.repository;

import com.hotelbooking.domain.Money;
import com.hotelbooking.domain.RoomInventory;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simulates SQL atomic CAS via synchronized blocks per row key.
 * In production: UPDATE WHERE available_rooms >= n AND blocked = false.
 */
public final class RoomInventoryRepository {
    private record Key(UUID hotel, UUID roomType, LocalDate date) {}
    private final ConcurrentMap<Key, RoomInventory> store = new ConcurrentHashMap<>();

    public void seed(UUID hotelId, UUID roomTypeId, LocalDate date, int totalRooms, Money price) {
        store.put(new Key(hotelId, roomTypeId, date),
                  new RoomInventory(hotelId, roomTypeId, date, totalRooms, price));
    }

    public Optional<RoomInventory> find(UUID hotelId, UUID roomTypeId, LocalDate date) {
        return Optional.ofNullable(store.get(new Key(hotelId, roomTypeId, date)));
    }

    /** Atomic decrement, returns true if successful (i.e., enough inventory & not blocked). */
    public synchronized boolean decrement(UUID hotelId, UUID roomTypeId, LocalDate date, int n) {
        RoomInventory inv = store.get(new Key(hotelId, roomTypeId, date));
        if (inv == null) return false;
        if (inv.blocked()) return false;
        if (inv.availableRooms() < n) return false;
        accessUnsafeDecrement(inv, n);
        return true;
    }

    public synchronized void increment(UUID hotelId, UUID roomTypeId, LocalDate date, int n) {
        RoomInventory inv = store.get(new Key(hotelId, roomTypeId, date));
        if (inv != null) accessUnsafeIncrement(inv, n);
    }

    /** Helper since RoomInventory mutators are package-private to its package; here we cheat for demo. */
    private void accessUnsafeDecrement(RoomInventory inv, int n) {
        // Reflection-free: use a dedicated method exposed in domain via package-private.
        // For this skeleton we re-create with adjusted values:
        // (In real impl, just call inv.decrementUnsafe(n) which is package-private.)
        try {
            var m = RoomInventory.class.getDeclaredMethod("decrementUnsafe", int.class);
            m.setAccessible(true);
            m.invoke(inv, n);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void accessUnsafeIncrement(RoomInventory inv, int n) {
        try {
            var m = RoomInventory.class.getDeclaredMethod("incrementUnsafe", int.class);
            m.setAccessible(true);
            m.invoke(inv, n);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
