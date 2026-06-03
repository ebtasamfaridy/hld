package com.bookmyshow.inventory;

import com.bookmyshow.domain.SeatId;

import java.time.Duration;

public interface SeatLock {
    /** Try to lock (showId, seatId) for owner with TTL; returns true if won. */
    boolean tryHold(String showId, SeatId seat, String owner, Duration ttl);

    /** Release the lock; only succeeds if current holder matches owner. */
    boolean release(String showId, SeatId seat, String owner);

    /** Returns true if seat is currently held (regardless of owner). */
    boolean isHeld(String showId, SeatId seat);

    /** Returns true if seat is held by exactly this owner. */
    boolean isHeldBy(String showId, SeatId seat, String owner);
}
