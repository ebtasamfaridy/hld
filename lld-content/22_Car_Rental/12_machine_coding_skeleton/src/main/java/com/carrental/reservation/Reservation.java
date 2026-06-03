package com.carrental.reservation;

import com.carrental.domain.Enums.ReservationStatus;
import com.carrental.domain.Ids;
import com.carrental.domain.Money;
import com.carrental.domain.TimeWindow;
import com.carrental.payment.Payment;

import java.time.Instant;
import java.util.UUID;

public final class Reservation {
    private final UUID id;
    private final UUID userId;
    private final UUID vehicleId;
    private final TimeWindow window;
    private final Money baseFare;
    private final Money deposit;
    private final String idempotencyKey;
    private final Instant createdAt;

    private volatile ReservationStatus status;
    private volatile Payment payment;

    public Reservation(UUID userId, UUID vehicleId, TimeWindow window,
                       Money baseFare, Money deposit, String idempotencyKey) {
        this.id = Ids.newId();
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.window = window;
        this.baseFare = baseFare;
        this.deposit = deposit;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.status = ReservationStatus.HELD;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID vehicleId() { return vehicleId; }
    public TimeWindow window() { return window; }
    public Money baseFare() { return baseFare; }
    public Money deposit() { return deposit; }
    public String idempotencyKey() { return idempotencyKey; }
    public Instant createdAt() { return createdAt; }
    public ReservationStatus status() { return status; }
    public Payment payment() { return payment; }
    public void setPayment(Payment p) { this.payment = p; }

    /** Status-guarded transition; mirrors `UPDATE WHERE status='X'`. Returns true if transition happened. */
    public synchronized boolean transition(ReservationStatus from, ReservationStatus to) {
        if (this.status != from) return false;
        this.status = to;
        return true;
    }

    @Override public String toString() {
        return "Reservation{" + Ids.shortHex(id) + " status=" + status
                + " " + window.start() + "→" + window.end() + "}";
    }
}
