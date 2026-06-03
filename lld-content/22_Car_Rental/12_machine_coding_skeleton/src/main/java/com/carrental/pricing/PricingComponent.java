package com.carrental.pricing;

import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

public interface PricingComponent {
    String name();
    /** Return Money.zero(currency) when not applicable. */
    Money compute(Reservation r, Trip t);
}
