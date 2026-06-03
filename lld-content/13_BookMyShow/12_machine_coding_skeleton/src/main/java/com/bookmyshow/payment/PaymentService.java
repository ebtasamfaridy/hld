package com.bookmyshow.payment;

import com.bookmyshow.domain.Money;

public interface PaymentService {
    sealed interface Result permits Result.Success, Result.Failure {
        record Success(String paymentRef) implements Result {}
        record Failure(String reason) implements Result {}
    }
    Result charge(Money amount, String paymentToken, String idempotencyKey);
    Result refund(String paymentRef, String idempotencyKey);
}
