package com.bookmyshow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Show {
    private final String id;
    private final String movieTitle;
    private final Map<SeatId, Seat> seats;     // entire seat catalog
    private final Map<SeatCategory, Money> basePrice;
    private final Instant startsAt;
    private ShowStatus status = ShowStatus.OPEN;

    public Show(String id, String movieTitle, List<Seat> allSeats,
                Map<SeatCategory, Money> basePrice, Instant startsAt) {
        this.id = id; this.movieTitle = movieTitle;
        this.seats = allSeats.stream().collect(Collectors.toMap(Seat::id, s -> s));
        this.basePrice = Map.copyOf(basePrice);
        this.startsAt = startsAt;
    }

    public String id()             { return id; }
    public String movieTitle()     { return movieTitle; }
    public Map<SeatId, Seat> seats() { return seats; }
    public Money basePriceFor(SeatCategory c) { return basePrice.get(c); }
    public Instant startsAt()      { return startsAt; }
    public ShowStatus status()     { return status; }
    public void setStatus(ShowStatus s) { this.status = s; }
    public int totalSeats() { return seats.size(); }
}
