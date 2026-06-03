package com.hotelbooking.service;

import com.hotelbooking.domain.Booking;
import com.hotelbooking.domain.CancellationPolicy;
import com.hotelbooking.domain.Money;
import com.hotelbooking.repository.BookingRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class BookingService {
    private final BookingRepository bookings;
    private final InventoryService inventory;
    private final PricingService pricing;

    public BookingService(BookingRepository bookings, InventoryService inventory, PricingService pricing) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.pricing = pricing;
    }

    public Booking createBooking(UUID guestId, UUID hotelId, UUID roomTypeId,
                                 LocalDate checkIn, LocalDate checkOut, int roomCount,
                                 CancellationPolicy policy, String idempotencyKey) {
        Optional<Booking> existing = bookings.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        // 1. price quote
        Money total = pricing.quote(hotelId, roomTypeId, checkIn, checkOut, roomCount);

        // 2. reserve inventory atomically
        boolean reserved = inventory.reserveRange(hotelId, roomTypeId, checkIn, checkOut, roomCount);
        if (!reserved) throw new IllegalStateException("INVENTORY_UNAVAILABLE");

        // 3. create + confirm
        Booking b = new Booking(guestId, hotelId, roomTypeId, checkIn, checkOut, roomCount,
                                total, policy, idempotencyKey);
        b.confirm();
        return bookings.save(b);
    }

    public Booking cancel(UUID bookingId, Instant at) {
        Booking b = bookings.findById(bookingId).orElseThrow();
        Money refund = b.policy().refundFor(b, at);
        inventory.releaseRange(b.hotelId(), b.roomTypeId(), b.checkIn(), b.checkOut(), b.roomCount());
        b.cancel(refund);
        return bookings.save(b);
    }

    public Booking checkIn(UUID bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow();
        b.checkIn();
        return bookings.save(b);
    }

    public Booking checkOut(UUID bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow();
        b.checkOut();
        return bookings.save(b);
    }
}
