package com.carrental.payment;

import com.carrental.domain.Money;

import java.util.UUID;

public interface PaymentGateway {

    record AuthResult(boolean ok, String authId, String reason) {}
    record CaptureResult(boolean ok, String captureId, String reason) {}
    record RefundResult(boolean ok, String refundId, String reason) {}
    record ChargeResult(boolean ok, String chargeId, String reason) {}

    AuthResult authorize(UUID resvId, Money amount, String idempotencyKey);
    CaptureResult capture(String authId, Money amount, String idempotencyKey);
    boolean voidAuth(String authId);
    RefundResult refund(String captureId, Money amount, String idempotencyKey);
    /** Merchant-Initiated Transaction (e.g., delayed damage charge). */
    ChargeResult mit(String savedMethodId, Money amount, String idempotencyKey);
}
