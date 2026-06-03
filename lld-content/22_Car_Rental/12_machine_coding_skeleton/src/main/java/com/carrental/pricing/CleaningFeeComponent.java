package com.carrental.pricing;

import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

/**
 * Flat cleaning fee, only applied when ops marks the trip as needing cleaning.
 */
public final class CleaningFeeComponent implements PricingComponent {
    private final Money fee;
    public CleaningFeeComponent(Money fee) { this.fee = fee; }

    @Override public String name() { return "cleaning"; }
    @Override public Money compute(Reservation r, Trip t) {
        return t.needsCleaning() ? fee : Money.zero(r.baseFare().currency());
    }
}
