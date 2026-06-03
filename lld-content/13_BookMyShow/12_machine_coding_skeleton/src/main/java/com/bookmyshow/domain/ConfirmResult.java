package com.bookmyshow.domain;

public sealed interface ConfirmResult
        permits ConfirmResult.Confirmed, ConfirmResult.Expired,
                ConfirmResult.PaymentFailed, ConfirmResult.SeatConflict {
    record Confirmed(Booking booking) implements ConfirmResult {}
    record Expired() implements ConfirmResult {}
    record PaymentFailed(String reason) implements ConfirmResult {}
    record SeatConflict() implements ConfirmResult {}
}
