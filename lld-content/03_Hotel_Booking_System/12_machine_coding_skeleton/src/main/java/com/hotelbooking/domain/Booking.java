package com.hotelbooking.domain;

import java.time.LocalDate;
import java.util.UUID;

public final class Booking {
    private final UUID id;
    private final UUID guestId;
    private final UUID hotelId;
    private final UUID roomTypeId;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private final int roomCount;
    private final String idempotencyKey;
    private final CancellationPolicy policy;

    private BookingStatus status;
    private Money totalPrice;
    private Money refundedAmount;
    private long version;

    public Booking(UUID guestId, UUID hotelId, UUID roomTypeId,
                   LocalDate checkIn, LocalDate checkOut, int roomCount,
                   Money totalPrice, CancellationPolicy policy, String idempotencyKey) {
        if (!checkOut.isAfter(checkIn)) throw new IllegalArgumentException("checkOut must be after checkIn");
        if (roomCount < 1) throw new IllegalArgumentException("roomCount >= 1");
        this.id = UUID.randomUUID();
        this.guestId = guestId;
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.roomCount = roomCount;
        this.totalPrice = totalPrice;
        this.policy = policy;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING;
        this.refundedAmount = Money.zero(totalPrice.currency());
    }

    public void confirm() { transition(BookingStatus.CONFIRMED); }
    public void checkIn() { transition(BookingStatus.CHECKED_IN); }
    public void checkOut() { transition(BookingStatus.CHECKED_OUT); }
    public void noShow() { transition(BookingStatus.NO_SHOW); }
    public void cancel(Money refund) {
        transition(BookingStatus.CANCELLED);
        this.refundedAmount = refund;
    }

    private void transition(BookingStatus next) {
        BookingStatus.requireTransition(status, next);
        status = next;
        version++;
    }

    public UUID id() { return id; }
    public UUID guestId() { return guestId; }
    public UUID hotelId() { return hotelId; }
    public UUID roomTypeId() { return roomTypeId; }
    public LocalDate checkIn() { return checkIn; }
    public LocalDate checkOut() { return checkOut; }
    public int roomCount() { return roomCount; }
    public BookingStatus status() { return status; }
    public Money totalPrice() { return totalPrice; }
    public Money refundedAmount() { return refundedAmount; }
    public CancellationPolicy policy() { return policy; }
    public String idempotencyKey() { return idempotencyKey; }
    public long version() { return version; }

    @Override public String toString() {
        return "Booking{id=" + id + ", " + status + ", " + checkIn + " -> " + checkOut
             + ", rooms=" + roomCount + ", total=" + totalPrice
             + (refundedAmount.isPositive() ? ", refunded=" + refundedAmount : "") + "}";
    }
}
