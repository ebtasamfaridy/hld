package com.bookmyshow;

import com.bookmyshow.booking.BookingService;
import com.bookmyshow.domain.*;
import com.bookmyshow.inventory.InMemorySeatLock;
import com.bookmyshow.payment.StubPaymentService;
import com.bookmyshow.pricing.BasePlusSurgePricing;
import com.bookmyshow.repository.BookingRepository;
import com.bookmyshow.repository.HoldRepository;
import com.bookmyshow.repository.ShowRepository;

import java.time.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        // Mutable virtual clock so we can simulate hold expiry.
        final Instant[] now = { Instant.parse("2026-04-29T18:00:00Z") };
        Clock clock = new Clock() {
            @Override public Instant instant()     { return now[0]; }
            @Override public ZoneId getZone()      { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };

        // Build show with 5×5 layout: row A premium, B-D standard, E premium.
        List<Seat> seats = new ArrayList<>();
        for (char r : new char[]{'A','B','C','D','E'}) {
            SeatCategory cat = (r == 'A' || r == 'E') ? SeatCategory.PREMIUM : SeatCategory.STANDARD;
            for (int c = 1; c <= 5; c++) seats.add(new Seat(SeatId.of(r, c), cat));
        }
        Map<SeatCategory, Money> base = new EnumMap<>(SeatCategory.class);
        base.put(SeatCategory.STANDARD, Money.inr(20000));   // ₹200
        base.put(SeatCategory.PREMIUM,  Money.inr(35000));   // ₹350

        Show show = new Show("show-1", "Tenet", seats, base, now[0].plus(Duration.ofHours(2)));

        var showRepo = new ShowRepository.InMemory();
        showRepo.add(show);

        var service = new BookingService(
                showRepo,
                new HoldRepository.InMemory(),
                new BookingRepository.InMemory(),
                new InMemorySeatLock(clock),
                new BasePlusSurgePricing(Money.inr(2000)),  // ₹20 conv fee
                new StubPaymentService(),
                clock,
                Duration.ofMinutes(10));

        section("User A holds A1, A2");
        var r1 = service.createHold("u-A", "show-1", List.of(SeatId.of('A',1), SeatId.of('A',2)));
        System.out.println("  " + r1);

        section("User B tries A2, A3 → conflict on A2");
        var r2 = service.createHold("u-B", "show-1", List.of(SeatId.of('A',2), SeatId.of('A',3)));
        System.out.println("  " + r2);

        section("User A confirms with valid payment");
        if (r1 instanceof HoldResult.Created c) {
            var conf = service.confirm(c.hold().id(), "TOKEN-OK", "idem-A");
            System.out.println("  " + conf);
        }

        section("User C holds A3");
        var r3 = service.createHold("u-C", "show-1", List.of(SeatId.of('A',3)));
        System.out.println("  " + r3);

        section("Advance 11 minutes → User C's hold expired; confirm fails");
        now[0] = now[0].plus(Duration.ofMinutes(11));
        if (r3 instanceof HoldResult.Created c) {
            var conf = service.confirm(c.hold().id(), "TOKEN-OK", "idem-C");
            System.out.println("  " + conf);
        }

        section("User D can now hold A3 (expired hold released the seat)");
        var r4 = service.createHold("u-D", "show-1", List.of(SeatId.of('A',3)));
        System.out.println("  " + r4);
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
