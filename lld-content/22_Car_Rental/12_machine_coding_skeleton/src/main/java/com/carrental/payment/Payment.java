package com.carrental.payment;

import com.carrental.domain.Enums.PaymentStatus;
import com.carrental.domain.Ids;
import com.carrental.domain.Money;

import java.util.UUID;

public final class Payment {
    private final UUID id;
    private final UUID reservationId;
    private final Money amountAuth;
    private final String idempotencyKey;
    private volatile PaymentStatus status = PaymentStatus.CREATED;
    private volatile String authId;
    private volatile String captureId;
    private volatile Money captured;
    private volatile Money refunded;

    public Payment(UUID reservationId, Money amount, String idempotencyKey) {
        this.id = Ids.newId();
        this.reservationId = reservationId;
        this.amountAuth = amount;
        this.idempotencyKey = idempotencyKey;
        this.captured = Money.zero(amount.currency());
        this.refunded = Money.zero(amount.currency());
    }

    public UUID id() { return id; }
    public UUID reservationId() { return reservationId; }
    public Money amountAuth() { return amountAuth; }
    public PaymentStatus status() { return status; }
    public String authId() { return authId; }
    public String captureId() { return captureId; }
    public String idempotencyKey() { return idempotencyKey; }
    public Money captured() { return captured; }
    public Money refunded() { return refunded; }

    public void markAuthorized(String authId) { this.authId = authId; this.status = PaymentStatus.AUTHORIZED; }
    public void markCaptured(String captureId, Money amount) {
        this.captureId = captureId;
        this.captured = amount;
        this.status = PaymentStatus.CAPTURED;
    }
    public void markVoided() { this.status = PaymentStatus.VOIDED; }
    public void markFailed() { this.status = PaymentStatus.FAILED; }
    public void addRefund(Money amount) {
        this.refunded = this.refunded.add(amount);
        this.status = (refunded.minor() >= captured.minor() && captured.isPositive())
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }
}
