package com.parking;

import com.parking.allocation.NearestEntranceAllocation;
import com.parking.domain.*;
import com.parking.gateway.EntryResult;
import com.parking.listener.ConsoleLogger;
import com.parking.pricing.FlatHourlyPricing;
import com.parking.pricing.FreeFirstWindowPricing;
import com.parking.repository.InMemorySpotRepository;
import com.parking.repository.InMemoryTicketRepository;

import java.time.*;
import java.util.EnumMap;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        var spotRepo = new InMemorySpotRepository();
        seedLot(spotRepo);

        Map<SpotType, Money> rate = new EnumMap<>(SpotType.class);
        rate.put(SpotType.BIKE,    Money.inr(1000));   // ₹10/hr
        rate.put(SpotType.COMPACT, Money.inr(3000));   // ₹30/hr
        rate.put(SpotType.LARGE,   Money.inr(5000));   // ₹50/hr
        rate.put(SpotType.EV,      Money.inr(7000));   // ₹70/hr (incl charging)

        var pricing = new FreeFirstWindowPricing(15, new FlatHourlyPricing(rate));

        // Simulate a clock we can advance.
        final Instant[] now = { Instant.parse("2026-04-29T10:00:00Z") };
        Clock clock = new Clock() {
            @Override public Instant instant() { return now[0]; }
            @Override public ZoneId getZone()  { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };

        var lot = new ParkingLot(spotRepo, new InMemoryTicketRepository(),
                new NearestEntranceAllocation(), pricing, new ConsoleLogger(), clock);

        section("Entries");
        var bike = Vehicle.of("MH04A001", VehicleType.BIKE);
        var car  = Vehicle.of("MH04B001", VehicleType.CAR);
        var ev   = Vehicle.of("MH04C001", VehicleType.EV_CAR);
        var truck= Vehicle.of("MH04D001", VehicleType.TRUCK);

        var r1 = lot.requestEntry(bike);
        var r2 = lot.requestEntry(car);
        var r3 = lot.requestEntry(ev);
        var r4 = lot.requestEntry(truck);

        section("Advance 90 minutes; exit Car");
        now[0] = now[0].plus(Duration.ofMinutes(90));
        if (r2 instanceof EntryResult.Admitted a) {
            System.out.println("  quoted: " + lot.quote(a.ticketId()));
            lot.settle(a.ticketId(), "pay-ref-001");
        }

        section("Try a 2nd truck after the 1st truck took the only LARGE on F1");
        var truck2 = Vehicle.of("MH04D002", VehicleType.TRUCK);
        lot.requestEntry(truck2);
    }

    private static void seedLot(InMemorySpotRepository repo) {
        // Floor 1: 3 BIKE, 3 COMPACT, 1 LARGE
        seed(repo, 1, 1, 1, 3, SpotType.BIKE);
        seed(repo, 1, 2, 1, 3, SpotType.COMPACT);
        seed(repo, 1, 3, 1, 1, SpotType.LARGE);
        // Floor 2: 2 LARGE, 2 EV
        seed(repo, 2, 1, 1, 2, SpotType.LARGE);
        seed(repo, 2, 2, 1, 2, SpotType.EV);
    }

    private static void seed(InMemorySpotRepository repo, int floor, int row, int firstCol, int count, SpotType type) {
        for (int c = firstCol; c < firstCol + count; c++) {
            repo.add(new Spot(SpotId.of(floor, row, c), floor, row, c, type));
        }
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
