package com.bookmyshow.pricing;

import com.bookmyshow.domain.PriceQuote;
import com.bookmyshow.domain.SeatId;
import com.bookmyshow.domain.Show;

import java.util.List;

public interface PricingPolicy {
    PriceQuote quote(Show show, List<SeatId> seats, int currentlyBookedCount);
}
