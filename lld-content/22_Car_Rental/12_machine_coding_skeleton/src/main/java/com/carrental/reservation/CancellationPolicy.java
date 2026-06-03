package com.carrental.reservation;

import com.carrental.domain.Money;

import java.time.Duration;
import java.time.Instant;

public interface CancellationPolicy {
    record Decision(Money refund, String tier) {}
    Decision refundFor(Reservation r, Instant now);

    /** Default Zoomcar-style tiers. */
    static CancellationPolicy tiered() {
        return (r, now) -> {
            Duration toPickup = Duration.between(now, r.window().start());
            long hours = toPickup.toHours();
            String currency = r.deposit().currency();

            // 100% > 24 h
            if (hours >= 24) return new Decision(r.deposit(), "TIER_100");
            // 50% 6–24 h
            if (hours >= 6)  return new Decision(half(r.deposit()), "TIER_50");
            // 25% 2–6 h
            if (hours >= 2)  return new Decision(quarter(r.deposit()), "TIER_25");
            // < 2 h: no refund
            return new Decision(Money.zero(currency), "TIER_0");
        };
    }

    private static Money half(Money m) { return Money.fromMinor(m.minor() / 2, m.currency()); }
    private static Money quarter(Money m) { return Money.fromMinor(m.minor() / 4, m.currency()); }
}
