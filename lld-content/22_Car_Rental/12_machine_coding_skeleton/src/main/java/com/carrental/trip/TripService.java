package com.carrental.trip;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.domain.Enums.ReservationStatus;
import com.carrental.domain.GeoPoint;
import com.carrental.domain.Money;
import com.carrental.payment.Payment;
import com.carrental.payment.PaymentGateway;
import com.carrental.payment.PaymentService;
import com.carrental.pricing.CompositePricing;
import com.carrental.reservation.Reservation;
import com.carrental.store.ReservationRepository;
import com.carrental.store.TripRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class TripService {

    private static final double FENCE_RADIUS_METRES = 50.0;
    private static final Duration EARLY_PICKUP_GRACE = Duration.ofMinutes(15);
    private static final Duration LATE_PICKUP_GRACE  = Duration.ofMinutes(30);

    private final ReservationRepository reservations;
    private final TripRepository trips;
    private final CatalogService catalog;
    private final IoTAdapter iot;
    private final CompositePricing pricing;
    private final PaymentService payments;
    private final Clock clock;

    public TripService(ReservationRepository reservations, TripRepository trips,
                       CatalogService catalog, IoTAdapter iot,
                       CompositePricing pricing, PaymentService payments, Clock clock) {
        this.reservations = reservations;
        this.trips = trips;
        this.catalog = catalog;
        this.iot = iot;
        this.pricing = pricing;
        this.payments = payments;
        this.clock = clock;
    }

    /** Validate fence + window + status, then unlock and create the Trip. */
    public Trip start(UUID reservationId, GeoPoint gps, int odoStart, int fuelStart, String idemToken) {
        Reservation r = reservations.byId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        Vehicle v = catalog.requireVehicle(r.vehicleId());

        if (r.status() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("reservation not ready: " + r.status());
        }

        Instant now = clock.instant();
        Instant earliest = r.window().start().minus(EARLY_PICKUP_GRACE);
        Instant latest   = r.window().start().plus(LATE_PICKUP_GRACE);
        if (now.isBefore(earliest) || now.isAfter(latest)) {
            throw new IllegalStateException("outside pickup window");
        }

        double dist = gps.distanceMetres(v.location());
        if (dist > FENCE_RADIUS_METRES) {
            throw new IllegalStateException("GPS out of fence: " + (int) dist + "m");
        }

        // IoT unlock with idempotency token
        iot.unlock(v.id(), idemToken);

        Trip t = new Trip(reservationId, now, odoStart, fuelStart, gps);
        trips.save(t);
        r.transition(ReservationStatus.CONFIRMED, ReservationStatus.ACTIVE);
        return t;
    }

    /** End the trip, compute fare, capture payment. Returns the breakdown. */
    public CompositePricing.Breakdown end(UUID tripId, GeoPoint gps, int odoEnd, int fuelEnd,
                                          boolean needsCleaning) {
        Trip t = trips.byId(tripId).orElseThrow();
        Reservation r = reservations.byId(t.reservationId()).orElseThrow();

        t.markReturned(clock.instant(), odoEnd, fuelEnd, gps, needsCleaning);
        CompositePricing.Breakdown bd = pricing.breakdown(r, t);
        t.setFinalFare(bd.total());

        // Capture up to deposit; if total > deposit, MIT for the difference.
        Payment p = r.payment();
        Money deposit = r.deposit();
        if (bd.total().minor() <= deposit.minor()) {
            payments.captureUpTo(p, bd.total());
        } else {
            payments.captureUpTo(p, deposit);
            Money diff = bd.total().subtract(deposit);
            String idem = "diff-" + r.idempotencyKey();
            PaymentGateway.ChargeResult mit = payments.mit("saved-method-" + r.userId(), diff, idem);
            if (!mit.ok()) {
                System.out.println("  [WARN] MIT charge failed: " + mit.reason() + " — user moves to DUNNING");
            }
        }

        r.transition(ReservationStatus.ACTIVE, ReservationStatus.COMPLETED);

        // Update vehicle state from the trip
        Vehicle v = catalog.requireVehicle(r.vehicleId());
        v.updateState(gps, fuelEnd, odoEnd);
        return bd;
    }
}
