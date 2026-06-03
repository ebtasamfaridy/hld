package com.parking.pricing;

import com.parking.domain.Money;
import com.parking.domain.SpotType;
import com.parking.domain.Ticket;

import java.time.Instant;

public interface PricingStrategy {
    Money compute(Ticket ticket, Instant exit, SpotType spotType);
}
