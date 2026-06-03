package com.bookmyshow.repository;

import com.bookmyshow.domain.Booking;
import com.bookmyshow.domain.SeatId;

import java.util.*;

public interface BookingRepository {
    /** Returns true if no overlap with existing CONFIRMED bookings on these seats. */
    boolean trySaveAtomically(Booking b);
    Optional<Booking> get(String id);
    int countConfirmedSeats(String showId);

    final class InMemory implements BookingRepository {
        private final Map<String, Booking> map = new HashMap<>();
        // (showId, seatId) → bookingId (for the PK guard analog)
        private final Map<String, String> seatGuard = new HashMap<>();

        @Override
        public boolean trySaveAtomically(Booking b) {
            // Check no seat conflict.
            for (var s : b.seats()) {
                String k = b.showId() + ":" + s.seatId();
                if (seatGuard.containsKey(k)) return false;
            }
            // Reserve atomically.
            for (var s : b.seats()) {
                seatGuard.put(b.showId() + ":" + s.seatId(), b.id());
            }
            map.put(b.id(), b);
            return true;
        }

        @Override public Optional<Booking> get(String id) { return Optional.ofNullable(map.get(id)); }

        @Override
        public int countConfirmedSeats(String showId) {
            int n = 0;
            for (Booking b : map.values()) {
                if (b.showId().equals(showId) && b.status() == Booking.Status.CONFIRMED) n += b.seats().size();
            }
            return n;
        }
    }
}
