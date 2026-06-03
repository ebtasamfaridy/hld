package com.bookmyshow.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Booking {
    public enum Status { CONFIRMED, CANCELLED, REFUNDED }

    private final String id;
    private final String userId;
    private final String showId;
    private final String holdId;
    private final List<BookedSeat> seats;
    private final Money total;
    private final String paymentRef;
    private final Instant createdAt;
    private Status status = Status.CONFIRMED;

    public Booking(String userId, String showId, String holdId,
                   List<BookedSeat> seats, Money total, String paymentRef, Instant createdAt) {
        this.id = "b-" + UUID.randomUUID();
        this.userId = userId; this.showId = showId; this.holdId = holdId;
        this.seats = List.copyOf(seats); this.total = total;
        this.paymentRef = paymentRef; this.createdAt = createdAt;
    }

    public String id()              { return id; }
    public String userId()          { return userId; }
    public String showId()          { return showId; }
    public String holdId()          { return holdId; }
    public List<BookedSeat> seats() { return seats; }
    public Money total()            { return total; }
    public String paymentRef()      { return paymentRef; }
    public Status status()          { return status; }

    public void cancel()  { this.status = Status.CANCELLED; }
    public void refund()  { this.status = Status.REFUNDED; }
}
