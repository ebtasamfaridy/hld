package com.ridebooking.matching;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.Ride;
import com.ridebooking.repository.DriverRepository;

import java.util.Comparator;
import java.util.Optional;

public final class MatchingService {
    private final DriverRepository drivers;
    private final ScoringStrategy scorer;

    public MatchingService(DriverRepository drivers, ScoringStrategy scorer) {
        this.drivers = drivers;
        this.scorer = scorer;
    }

    /**
     * Find best driver for the ride.
     * Synchronized to simulate atomic CAS in this in-memory toy.
     * In production: optimistic UPDATE WHERE status=IDLE AND version=?
     */
    public synchronized Optional<Driver> match(Ride ride, String city) {
        Optional<Driver> best = drivers.findIdleByCityAndType(city, ride.type()).stream()
                .max(Comparator.comparingDouble(d -> scorer.score(d, ride.pickup())));
        if (best.isEmpty()) return Optional.empty();

        Driver d = best.get();
        d.reserve();
        drivers.save(d);
        return Optional.of(d);
    }
}
