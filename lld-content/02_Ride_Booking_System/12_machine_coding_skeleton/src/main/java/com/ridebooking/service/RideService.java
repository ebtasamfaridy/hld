package com.ridebooking.service;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.FareEstimate;
import com.ridebooking.domain.FareFinal;
import com.ridebooking.domain.Location;
import com.ridebooking.domain.Money;
import com.ridebooking.domain.Ride;
import com.ridebooking.domain.RideStatus;
import com.ridebooking.domain.RideType;
import com.ridebooking.matching.MatchingService;
import com.ridebooking.pricing.PricingService;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.RideRepository;

import java.util.Optional;
import java.util.UUID;

public final class RideService {
    private final RideRepository rides;
    private final DriverRepository drivers;
    private final PricingService pricing;
    private final MatchingService matcher;

    public RideService(RideRepository rides, DriverRepository drivers,
                       PricingService pricing, MatchingService matcher) {
        this.rides = rides;
        this.drivers = drivers;
        this.pricing = pricing;
        this.matcher = matcher;
    }

    public FareEstimate estimate(Location pickup, Location drop, RideType type) {
        return pricing.estimate(pickup, drop, type);
    }

    public Ride request(UUID riderId, Location pickup, Location drop,
                        RideType type, String idempotencyKey, FareEstimate estimate) {
        Optional<Ride> existing = rides.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        Ride r = Ride.builder()
                .rider(riderId)
                .type(type)
                .pickup(pickup)
                .drop(drop)
                .estimate(estimate)
                .surge(estimate.surgeFactor())
                .idempotencyKey(idempotencyKey)
                .build();
        return rides.save(r);
    }

    public Optional<Ride> matchAndAssign(UUID rideId, String city) {
        Ride r = rides.findById(rideId).orElseThrow();
        Optional<Driver> chosen = matcher.match(r, city);
        if (chosen.isEmpty()) return Optional.empty();
        Driver d = chosen.get();
        r.match(d.id());
        rides.save(r);
        return Optional.of(r);
    }

    public Ride driverEnRoute(UUID rideId) {
        Ride r = rides.findById(rideId).orElseThrow();
        Driver d = drivers.findById(r.driverId()).orElseThrow();
        d.enRoute();
        drivers.save(d);
        r.arriving();
        return rides.save(r);
    }

    public Ride driverArrived(UUID rideId) {
        Ride r = rides.findById(rideId).orElseThrow();
        Driver d = drivers.findById(r.driverId()).orElseThrow();
        d.atPickup();
        drivers.save(d);
        r.arrived();
        return rides.save(r);
    }

    public Ride startTrip(UUID rideId, String otp) {
        Ride r = rides.findById(rideId).orElseThrow();
        Driver d = drivers.findById(r.driverId()).orElseThrow();
        r.start(otp);          // validates OTP + state
        d.startTrip();
        drivers.save(d);
        return rides.save(r);
    }

    public Ride endTrip(UUID rideId, double km, int minutes, Money tip) {
        Ride r = rides.findById(rideId).orElseThrow();
        Driver d = drivers.findById(r.driverId()).orElseThrow();
        FareFinal f = pricing.finalize(km, minutes, r.surgeFactorLocked(), tip);
        r.end(km, minutes, f);
        d.endTrip();
        drivers.save(d);
        return rides.save(r);
    }

    public Ride cancel(UUID rideId, Money fee) {
        Ride r = rides.findById(rideId).orElseThrow();
        if (r.driverId() != null) {
            drivers.findById(r.driverId()).ifPresent(d -> { d.backToIdle(); drivers.save(d); });
        }
        r.cancel(fee);
        return rides.save(r);
    }
}
