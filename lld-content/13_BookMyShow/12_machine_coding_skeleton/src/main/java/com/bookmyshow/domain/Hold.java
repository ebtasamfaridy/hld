package com.bookmyshow.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Hold {
    public enum Status { HELD, CONFIRMED, EXPIRED, CANCELLED }

    private final String id;
    private final String userId;
    private final String showId;
    private final List<SeatId> seats;
    private final PriceQuote quote;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Status status = Status.HELD;
    private String confirmedBookingId;

    public Hold(String userId, String showId, List<SeatId> seats,
                PriceQuote quote, Instant createdAt, Instant expiresAt) {
        this.id = "h-" + UUID.randomUUID();
        this.userId = userId;
        this.showId = showId;
        this.seats = List.copyOf(seats);
        this.quote = quote;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String id()           { return id; }
    public String userId()       { return userId; }
    public String showId()       { return showId; }
    public List<SeatId> seats()  { return seats; }
    public PriceQuote quote()    { return quote; }
    public Instant expiresAt()   { return expiresAt; }
    public Status status()       { return status; }
    public String confirmedBookingId() { return confirmedBookingId; }

    public boolean isAlive(Instant now) {
        return status == Status.HELD && now.isBefore(expiresAt);
    }
    public void markConfirmed(String bookingId) {
        if (status != Status.HELD) throw new IllegalStateException("not held");
        this.status = Status.CONFIRMED;
        this.confirmedBookingId = bookingId;
    }
    public void markExpired()    { if (status == Status.HELD) this.status = Status.EXPIRED; }
    public void markCancelled()  { if (status == Status.HELD) this.status = Status.CANCELLED; }
}
