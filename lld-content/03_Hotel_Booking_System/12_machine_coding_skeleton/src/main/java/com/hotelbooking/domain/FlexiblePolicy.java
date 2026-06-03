package com.hotelbooking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Free until 48 h before check-in; else 50% refund. */
public final class FlexiblePolicy implements CancellationPolicy {
    @Override public Money refundFor(Booking b, Instant cancelAt) {
        Instant checkInInstant = b.checkIn().atStartOfDay().toInstant(ZoneOffset.UTC);
        long hours = ChronoUnit.HOURS.between(cancelAt, checkInInstant);
        if (hours >= 48) return b.totalPrice();
        return b.totalPrice().multiply(new BigDecimal("0.5"));
    }
}
