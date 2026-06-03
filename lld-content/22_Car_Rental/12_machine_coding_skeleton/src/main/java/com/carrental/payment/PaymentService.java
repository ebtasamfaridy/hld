package com.carrental.payment;

import com.carrental.domain.Money;
import com.carrental.store.PaymentRepository;

import java.util.UUID;

public final class PaymentService {
    private final PaymentRepository repo;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository repo, PaymentGateway gateway) {
        this.repo = repo;
        this.gateway = gateway;
    }

    public Payment authorize(UUID reservationId, Money amount, String idempotencyKey) {
        Payment p = new Payment(reservationId, amount, idempotencyKey);
        repo.save(p);
        PaymentGateway.AuthResult r = gateway.authorize(reservationId, amount, idempotencyKey);
        if (!r.ok()) {
            p.markFailed();
            throw new PaymentDeclined(r.reason());
        }
        p.markAuthorized(r.authId());
        return p;
    }

    /** Capture exactly the given amount up to the authorized limit; void any unused remainder. */
    public Payment captureUpTo(Payment p, Money toCapture) {
        if (toCapture.greaterThan(p.amountAuth())) {
            // The base auth wasn't enough — capture full auth here; caller must MIT for the difference.
            toCapture = p.amountAuth();
        }
        PaymentGateway.CaptureResult r = gateway.capture(
                p.authId(), toCapture, "cap-" + p.idempotencyKey());
        if (!r.ok()) throw new IllegalStateException("capture failed: " + r.reason());
        p.markCaptured(r.captureId(), toCapture);
        if (toCapture.minor() < p.amountAuth().minor()) {
            // Best-effort void of unused auth; safe to call again
            gateway.voidAuth(p.authId());
        }
        return p;
    }

    public PaymentGateway.ChargeResult mit(String savedMethod, Money amount, String idemKey) {
        return gateway.mit(savedMethod, amount, idemKey);
    }

    public void voidAuth(Payment p) {
        if (p.authId() != null) {
            gateway.voidAuth(p.authId());
            p.markVoided();
        }
    }

    public void refund(Payment p, Money amount) {
        PaymentGateway.RefundResult r = gateway.refund(
                p.captureId(), amount, "ref-" + p.idempotencyKey() + "-" + System.nanoTime());
        if (!r.ok()) throw new IllegalStateException("refund failed: " + r.reason());
        p.addRefund(amount);
    }

    public static final class PaymentDeclined extends RuntimeException {
        public PaymentDeclined(String r) { super(r); }
    }
}
