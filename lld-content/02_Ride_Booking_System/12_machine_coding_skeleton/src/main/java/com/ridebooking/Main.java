package com.ridebooking;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.FareEstimate;
import com.ridebooking.domain.Location;
import com.ridebooking.domain.Money;
import com.ridebooking.domain.Ride;
import com.ridebooking.domain.RideType;
import com.ridebooking.domain.Vehicle;
import com.ridebooking.matching.MatchingService;
import com.ridebooking.matching.WeightedScoring;
import com.ridebooking.pricing.PricingService;
import com.ridebooking.pricing.SurgeService;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.RideRepository;
import com.ridebooking.service.RideService;

import java.math.BigDecimal;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        // ── wiring
        var rideRepo = new RideRepository();
        var driverRepo = new DriverRepository();
        var surge = new SurgeService();
        var pricing = new PricingService(
                surge,
                Money.inr(40),     // base
                Money.inr(12),     // per km
                Money.inr(1.5),    // per min
                Money.inr(15),     // platform fee
                new BigDecimal("0.05"));
        var matcher = new MatchingService(driverRepo, new WeightedScoring(1.0, 0.3));
        var rides = new RideService(rideRepo, driverRepo, pricing, matcher);

        // ── seed drivers
        var aliceCar = new Vehicle("KA01AB1234", "Toyota", "Camry", RideType.STANDARD);
        var alice = driverRepo.save(new Driver(UUID.randomUUID(), "Alice", "BLR",
                aliceCar, new Location(12.971, 77.591), 4.7));
        alice.goOnline(); driverRepo.save(alice);

        var bobCar = new Vehicle("KA02CD5678", "Honda", "City", RideType.STANDARD);
        var bob = driverRepo.save(new Driver(UUID.randomUUID(), "Bob", "BLR",
                bobCar, new Location(12.985, 77.610), 4.5));
        bob.goOnline(); driverRepo.save(bob);

        // ── simulate surge in pickup zone
        Location pickup = new Location(12.97, 77.59);
        Location drop = new Location(13.00, 77.65);
        surge.set(pickup, RideType.STANDARD, new BigDecimal("1.4"));

        // ── 1. estimate
        FareEstimate est = rides.estimate(pickup, drop, RideType.STANDARD);
        System.out.println("Estimate: " + est);

        // ── 2. request
        UUID riderId = UUID.randomUUID();
        Ride r = rides.request(riderId, pickup, drop, RideType.STANDARD, "ride-key-1", est);
        System.out.println("Requested: " + r);

        // idempotent retry
        Ride r2 = rides.request(riderId, pickup, drop, RideType.STANDARD, "ride-key-1", est);
        System.out.println("Idempotent: same? " + r.id().equals(r2.id()));

        // ── 3. match
        rides.matchAndAssign(r.id(), "BLR");
        Ride matched = rideRepo.findById(r.id()).orElseThrow();
        System.out.println("Matched: " + matched);

        // ── 4. lifecycle
        rides.driverEnRoute(r.id());
        rides.driverArrived(r.id());
        Ride mid = rideRepo.findById(r.id()).orElseThrow();
        System.out.println("OTP: " + mid.otp());

        rides.startTrip(r.id(), mid.otp());
        Ride done = rides.endTrip(r.id(), 7.5, 19, Money.inr(20));
        System.out.println("Completed: " + done);

        // ── 5. cancel after completed should fail
        try {
            rides.cancel(r.id(), Money.zero("INR"));
        } catch (IllegalStateException e) {
            System.out.println("Expected: cannot cancel completed ride");
        }
    }
}
