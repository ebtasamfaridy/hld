package com.parking.pricing;

import com.parking.domain.Money;
import com.parking.domain.SpotType;
import com.parking.domain.Ticket;

import java.time.Duration;
import java.time.Instant;

/** Decorator: zero-cost for the first N minutes, then delegate. */
public final class FreeFirstWindowPricing implements PricingStrategy {
    private final int freeMinutes;
    private final PricingStrategy inner;

    public FreeFirstWindowPricing(int freeMinutes, PricingStrategy inner) {
        this.freeMinutes = freeMinutes;
        this.inner = inner;
    }

    @Override
    public Money compute(Ticket t, Instant exit, SpotType spotType) {
        long minutes = Math.max(0, Duration.between(t.enteredAt(), exit).toMinutes());
        if (minutes <= freeMinutes) return Money.inr(0);
        return inner.compute(t, exit, spotType);
    }
}
