package com.carrental;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.catalog.VehicleModel;
import com.carrental.domain.GeoPoint;
import com.carrental.domain.Ids;
import com.carrental.domain.Money;
import com.carrental.domain.TimeWindow;
import com.carrental.inventory.SlotInventoryService;
import com.carrental.payment.FakeGateway;
import com.carrental.payment.PaymentService;
import com.carrental.pricing.*;
import com.carrental.reservation.CancellationPolicy;
import com.carrental.reservation.Reservation;
import com.carrental.reservation.ReservationService;
import com.carrental.store.*;
import com.carrental.trip.IoTAdapter;
import com.carrental.trip.Trip;
import com.carrental.trip.TripService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws Exception {
        // ------------------------------------------------------------------
        // Bootstrap with a controllable clock
        // ------------------------------------------------------------------
        final Instant[] now = { Instant.parse("2026-04-29T10:00:00Z") };
        Clock clock = new Clock() {
            @Override public Instant instant() { return now[0]; }
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };

        ModelRepository       models   = new ModelRepository();
        VehicleRepository     vehicles = new VehicleRepository();
        ReservationRepository resvRepo = new ReservationRepository();
        TripRepository        tripRepo = new TripRepository();
        PaymentRepository     payRepo  = new PaymentRepository();

        CatalogService catalog = new CatalogService(models, vehicles);
        SlotInventoryService inventory = new SlotInventoryService();
        FakeGateway gw = new FakeGateway();
        PaymentService payments = new PaymentService(payRepo, gw);
        ReservationService rs = new ReservationService(catalog, inventory, payments, resvRepo);
        IoTAdapter iot = IoTAdapter.stub();

        CompositePricing pricing = new CompositePricing(List.of(
                new BaseFareComponent(),
                new PerKmComponent(catalog),
                new LateFeeComponent(catalog),
                new FuelComponent(catalog),
                new CleaningFeeComponent(Money.inr(500))
        ));

        TripService trips = new TripService(resvRepo, tripRepo, catalog, iot, pricing, payments, clock);

        // ------------------------------------------------------------------
        // Seed catalog
        // ------------------------------------------------------------------
        VehicleModel swift = catalog.addModel(new VehicleModel(
                "Maruti Swift", 5, 37, Money.inr(180), Money.inr(6)));
        VehicleModel creta = catalog.addModel(new VehicleModel(
                "Hyundai Creta", 5, 50, Money.inr(250), Money.inr(8)));

        GeoPoint hsr = new GeoPoint(12.9116, 77.6473);
        Vehicle swiftCar = catalog.addVehicle(new Vehicle(
                swift.id(), "KA01CD5678", hsr, /*fuel*/ 80, /*odo*/ 45120));
        catalog.addVehicle(new Vehicle(
                creta.id(), "KA01AB1234", new GeoPoint(12.9279, 77.6271), 60, 30000));

        section("Demo 1 — Race for the same Swift on overlapping windows");
        UUID aman = Ids.newId();
        UUID maya = Ids.newId();
        TimeWindow amanWin = new TimeWindow(
                Instant.parse("2026-04-29T19:00:00Z"),
                Instant.parse("2026-05-02T09:00:00Z"));
        TimeWindow mayaWin = new TimeWindow(
                Instant.parse("2026-04-30T09:00:00Z"),
                Instant.parse("2026-05-01T17:00:00Z"));   // overlaps Aman's

        ConcurrentMap<String, Reservation> winners = new ConcurrentHashMap<>();
        AtomicInteger losses = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Thread tA = new Thread(() -> race(start,
                () -> rs.place(aman, swiftCar.id(), amanWin, "race-aman"),
                winners, losses, "Aman"));
        Thread tM = new Thread(() -> race(start,
                () -> rs.place(maya, swiftCar.id(), mayaWin, "race-maya"),
                winners, losses, "Maya"));
        tA.start(); tM.start();
        start.countDown();
        tA.join(); tM.join();

        System.out.printf("%n[Race result] wins=%d losses=%d%n", winners.size(), losses.get());

        // Pick whoever won as the actor for the rest of the demo.
        if (winners.isEmpty()) {
            System.out.println("Both lost — unexpected; ending demo.");
            return;
        }
        var entry = winners.entrySet().iterator().next();
        String winnerName = entry.getKey();
        Reservation r1 = entry.getValue();
        UUID winnerUser = r1.userId();
        TimeWindow winnerWin = r1.window();

        section("Demo 2 — Idempotency: same Idempotency-Key returns the same reservation");
        Reservation r2 = rs.place(winnerUser, swiftCar.id(), winnerWin,
                /* same key as the winner's race entry */ "race-" + winnerName.toLowerCase());
        System.out.printf("Same id?     %s%n", r1.id().equals(r2.id()));
        System.out.printf("authCount    %d (should be 1, not 2)%n", gw.authSnapshot().size());

        section("Demo 3 — Pickup, drive, return");
        now[0] = winnerWin.start().plusSeconds(5 * 60);          // 5 min after start time
        GeoPoint renterGps = new GeoPoint(12.9117, 77.6474);     // ~12 m from HSR pin
        Trip trip = trips.start(r1.id(), renterGps, /*odoStart*/ 45120, /*fuelStart*/ 80, "unlock-1");
        System.out.println("Pickup OK: " + trip);

        // 15 min before booked end → no late fee (within grace)
        now[0] = winnerWin.end().minus(Duration.ofMinutes(15));
        CompositePricing.Breakdown bd = trips.end(
                trip.id(), hsr,
                /*odoEnd*/ 45840,
                /*fuelEnd*/ 25,
                /*needsCleaning*/ false);
        printBreakdown("Final fare for " + winnerName, bd);

        section("Demo 4 — Late return triggers late fee");
        UUID karan = Ids.newId();
        TimeWindow karanWin = new TimeWindow(
                Instant.parse("2026-05-05T10:00:00Z"),
                Instant.parse("2026-05-05T13:00:00Z"));
        Reservation karanResv = rs.place(karan, swiftCar.id(), karanWin, "karan-1");
        now[0] = karanWin.start().plusSeconds(60);
        Trip karanTrip = trips.start(karanResv.id(), hsr, 45840, 25, "unlock-karan");

        // Returns 2.5 hr after booked end (grace 30 min, then 2 hr billable → 2× hourly × 2 hr)
        now[0] = karanWin.end().plus(Duration.ofHours(2).plusMinutes(30));
        CompositePricing.Breakdown karanBd = trips.end(karanTrip.id(), hsr, 45920, 20, false);
        printBreakdown("Late return — Karan", karanBd);

        section("Demo 5 — Cancellation tier (30 hr before pickup → 100% refund)");
        UUID priya = Ids.newId();
        TimeWindow priyaWin = new TimeWindow(
                Instant.parse("2026-05-10T19:00:00Z"),
                Instant.parse("2026-05-11T09:00:00Z"));
        Reservation priyaResv = rs.place(priya, swiftCar.id(), priyaWin, "priya-1");

        now[0] = priyaWin.start().minus(Duration.ofHours(30));
        ReservationService.CancelResult cr = rs.cancel(priyaResv.id(), now[0], CancellationPolicy.tiered());
        System.out.printf("tier=%s refund=%s%n", cr.tier(), cr.refund());

        section("Demo 6 — Slot conflict: try to book same window again");
        UUID rahul = Ids.newId();
        try {
            rs.place(rahul, swiftCar.id(), winnerWin, "rahul-conflict");
            System.out.println("Unexpected: should have conflicted");
        } catch (ReservationService.SlotConflict sc) {
            System.out.println("As expected, " + sc.blocked().size() + " slot(s) blocked → 409 OUT_OF_STOCK");
        }
    }

    private static void race(CountDownLatch latch, Attempt fn,
                             ConcurrentMap<String, Reservation> winners,
                             AtomicInteger losses, String name) {
        try {
            latch.await();
            Reservation r = fn.run();
            winners.put(name, r);
            System.out.println("  " + name + " → WON, reservation=" + Ids.shortHex(r.id()));
        } catch (ReservationService.SlotConflict sc) {
            losses.incrementAndGet();
            System.out.println("  " + name + " → LOST (slots conflict, " + sc.blocked().size() + " buckets)");
        } catch (Exception e) {
            losses.incrementAndGet();
            System.out.println("  " + name + " → LOST (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }
    }

    private static void printBreakdown(String label, CompositePricing.Breakdown bd) {
        System.out.println("\n" + label + ":");
        for (CompositePricing.Line line : bd.lines()) {
            System.out.println("    " + line.name() + ": " + line.amount());
        }
        System.out.println("    TOTAL: " + bd.total());
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }

    @FunctionalInterface
    private interface Attempt { Reservation run() throws Exception; }
}
