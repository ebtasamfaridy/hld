package com.carrental.pricing;

import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

public final class BaseFareComponent implements PricingComponent {
    @Override public String name() { return "base"; }
    @Override public Money compute(Reservation r, Trip t) {
        // base fare was locked at booking time
        return r.baseFare();
    }
}
