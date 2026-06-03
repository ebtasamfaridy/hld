package com.bookmyshow.inventory;

import com.bookmyshow.domain.SeatId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Mimics Redis SET NX EX. Lazily evicts expired holds on read/write. */
public final class InMemorySeatLock implements SeatLock {

    private record Entry(String owner, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySeatLock(Clock clock) { this.clock = clock; }

    private String k(String showId, SeatId seat) { return showId + ":" + seat; }

    @Override
    public boolean tryHold(String showId, SeatId seat, String owner, Duration ttl) {
        Instant now = clock.instant();
        String key = k(showId, seat);
        Entry attempt = new Entry(owner, now.plus(ttl));

        // compute() runs atomically per key — equivalent to Redis SETNX semantics.
        Entry stored = map.compute(key, (k, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) return attempt;
            return current;
        });
        return stored == attempt;
    }

    @Override
    public boolean release(String showId, SeatId seat, String owner) {
        String key = k(showId, seat);
        boolean[] released = { false };
        map.computeIfPresent(key, (k, current) -> {
            if (current.owner().equals(owner)) { released[0] = true; return null; }
            return current;
        });
        return released[0];
    }

    @Override
    public boolean isHeld(String showId, SeatId seat) {
        Instant now = clock.instant();
        Entry e = map.get(k(showId, seat));
        if (e == null) return false;
        if (e.expiresAt().isBefore(now)) { map.remove(k(showId, seat), e); return false; }
        return true;
    }

    @Override
    public boolean isHeldBy(String showId, SeatId seat, String owner) {
        Instant now = clock.instant();
        Entry e = map.get(k(showId, seat));
        if (e == null || e.expiresAt().isBefore(now)) return false;
        return owner.equals(e.owner());
    }
}
