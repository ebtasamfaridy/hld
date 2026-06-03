package com.bookmyshow.payment;

import com.bookmyshow.domain.Money;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StubPaymentService implements PaymentService {
    private final Map<String, Result> idempotencyCache = new HashMap<>();

    @Override
    public Result charge(Money amount, String paymentToken, String idempotencyKey) {
        Result cached = idempotencyCache.get("charge:" + idempotencyKey);
        if (cached != null) return cached;

        Result r;
        if (paymentToken != null && paymentToken.startsWith("FAIL")) {
            r = new Result.Failure("declined: " + paymentToken);
        } else {
            r = new Result.Success("pay-" + UUID.randomUUID());
        }
        idempotencyCache.put("charge:" + idempotencyKey, r);
        return r;
    }

    @Override
    public Result refund(String paymentRef, String idempotencyKey) {
        Result cached = idempotencyCache.get("refund:" + idempotencyKey);
        if (cached != null) return cached;
        Result r = new Result.Success("refund-" + UUID.randomUUID());
        idempotencyCache.put("refund:" + idempotencyKey, r);
        return r;
    }
}
