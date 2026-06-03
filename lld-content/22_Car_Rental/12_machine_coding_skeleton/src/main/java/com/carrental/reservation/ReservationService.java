package com.carrental.reservation;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.catalog.VehicleModel;
import com.carrental.domain.Enums.ReservationStatus;
import com.carrental.domain.Money;
import com.carrental.domain.TimeWindow;
import com.carrental.inventory.ReserveResult;
import com.carrental.inventory.SlotInventoryService;
import com.carrental.payment.Payment;
import com.carrental.payment.PaymentService;
import com.carrental.payment.PaymentService.PaymentDeclined;
import com.carrental.store.ReservationRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The place-reservation saga.
 *
 * Steps with compensations:
 *   1. Idempotency lookup
 *   2. Validate vehicle (active)
 *   3. Compute base fare + deposit
 *   4. Reserve slots (atomic)            ← compensation: release slots
 *   5. Authorize deposit on gateway      ← compensation: void auth
 *   6. Persist Reservation row
 *   7. Return reservation
 */
public final class ReservationService {

    private final CatalogService catalog;
    private final SlotInventoryService inventory;
    private final PaymentService payments;
    private final ReservationRepository reservations;

    /** Idempotency map: (userId, key) → reservationId */
    private final ConcurrentMap<String, UUID> idemIndex = new ConcurrentHashMap<>();

    public ReservationService(CatalogService catalog, SlotInventoryService inventory,
                              PaymentService payments, ReservationRepository reservations) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.payments = payments;
        this.reservations = reservations;
    }

    public Reservation place(UUID userId, UUID vehicleId, TimeWindow window, String idempotencyKey) {
        // 1. Idempotency
        String key = userId + "::" + idempotencyKey;
        UUID existing = idemIndex.get(key);
        if (existing != null) return reservations.byId(existing).orElseThrow();

        // 2. Validate vehicle
        Vehicle v = catalog.requireVehicle(vehicleId);
        if (!v.isReservable()) throw new IllegalStateException("vehicle not reservable: " + v);

        // 3. Compute fares
        VehicleModel m = catalog.requireModel(v.modelId());
        Money baseFare = m.hourlyRate().multiply(window.hours());
        Money deposit  = baseFare; // simple policy for the demo: full base fare authorized

        // 4. Reserve slots — atomic
        Reservation pending = new Reservation(userId, vehicleId, window, baseFare, deposit, idempotencyKey);
        ReserveResult rr = inventory.reserve(vehicleId, window, pending.id());
        if (rr instanceof ReserveResult.Conflict c) {
            throw new SlotConflict(c.blocked());
        }

        // 5. Authorize deposit
        Payment payment;
        try {
            payment = payments.authorize(pending.id(), deposit, idempotencyKey);
        } catch (PaymentDeclined d) {
            inventory.release(pending.id());
            throw d;
        }
        pending.setPayment(payment);

        // 6. Persist + transition to CONFIRMED
        pending.transition(ReservationStatus.HELD, ReservationStatus.CONFIRMED);
        reservations.save(pending);
        idemIndex.putIfAbsent(key, pending.id());
        return pending;
    }

    public CancelResult cancel(UUID reservationId, Instant now, CancellationPolicy policy) {
        Reservation r = reservations.byId(reservationId).orElseThrow();
        if (r.status() == ReservationStatus.ACTIVE || r.status() == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("cannot cancel; reservation is " + r.status());
        }
        if (r.status() == ReservationStatus.CANCELLED) {
            return new CancelResult(r, Money.zero(r.deposit().currency()), "ALREADY_CANCELLED");
        }

        CancellationPolicy.Decision decision = policy.refundFor(r, now);

        // Void the auth (gateway voids the reserved hold; the customer is charged refund amount only if forfeit)
        payments.voidAuth(r.payment());
        // For simplicity in the demo: auth is held, never captured at booking time, so void is correct.
        // If decision.refund < deposit, in reality we'd capture (deposit - refund) as forfeit.

        inventory.release(r.id());
        r.transition(r.status(), ReservationStatus.CANCELLED);
        return new CancelResult(r, decision.refund(), decision.tier());
    }

    public record CancelResult(Reservation reservation, Money refund, String tier) {}

    public static final class SlotConflict extends RuntimeException {
        private final java.util.List<com.carrental.domain.HourBucket> blocked;
        public SlotConflict(java.util.List<com.carrental.domain.HourBucket> blocked) {
            super("slots conflicted: " + blocked.size() + " buckets blocked");
            this.blocked = blocked;
        }
        public java.util.List<com.carrental.domain.HourBucket> blocked() { return blocked; }
    }
}
