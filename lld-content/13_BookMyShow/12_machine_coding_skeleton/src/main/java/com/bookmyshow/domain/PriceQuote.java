package com.bookmyshow.domain;

import java.util.Map;

public record PriceQuote(Money total, Map<SeatId, Money> perSeat,
                         Money convenienceFee, double surgeMultiplier) {}
