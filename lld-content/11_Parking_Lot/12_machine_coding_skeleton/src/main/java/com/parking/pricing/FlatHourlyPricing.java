package com.parking.pricing;

import com.parking.domain.Money;
import com.parking.domain.SpotType;
import com.parking.domain.Ticket;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public final class FlatHourlyPricing implements PricingStrategy {
    private final Map<SpotType, Money> ratePerHour;

    public FlatHourlyPricing(Map<SpotType, Money> ratePerHour) {
        this.ratePerHour = new EnumMap<>(ratePerHour);
    }

    @Override
    public Money compute(Ticket t, Instant exit, SpotType spotType) {
        long minutes = Math.max(0, Duration.between(t.enteredAt(), exit).toMinutes());
        long hours = (minutes + 59) / 60;
        Money rate = ratePerHour.getOrDefault(spotType, Money.inr(2000));
        return rate.times(hours);
    }
}
