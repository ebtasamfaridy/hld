package com.hotelbooking.domain;

import java.time.Instant;

public interface CancellationPolicy {
    Money refundFor(Booking booking, Instant cancelAt);
}
