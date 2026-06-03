package com.bookmyshow.pricing;

import com.bookmyshow.domain.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BasePlusSurgePricing implements PricingPolicy {

    private final Money convenienceFee;
    private final double surgeAt70 = 1.20;
    private final double surgeAt90 = 1.50;

    public BasePlusSurgePricing(Money convenienceFee) {
        this.convenienceFee = convenienceFee;
    }

    @Override
    public PriceQuote quote(Show show, List<SeatId> seats, int currentlyBookedCount) {
        double occupancy = (double) currentlyBookedCount / Math.max(1, show.totalSeats());
        double mult = (occupancy > 0.90) ? surgeAt90 : (occupancy > 0.70 ? surgeAt70 : 1.00);

        Map<SeatId, Money> per = new HashMap<>();
        Money total = Money.inr(0);
        for (SeatId id : seats) {
            Seat s = show.seats().get(id);
            if (s == null) throw new IllegalArgumentException("unknown seat " + id);
            Money p = show.basePriceFor(s.category()).times(mult);
            per.put(id, p);
            total = total.plus(p);
        }
        total = total.plus(convenienceFee);
        return new PriceQuote(total, per, convenienceFee, mult);
    }
}
