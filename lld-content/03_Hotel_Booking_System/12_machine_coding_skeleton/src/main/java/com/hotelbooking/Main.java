package com.hotelbooking;

import com.hotelbooking.domain.Booking;
import com.hotelbooking.domain.FlexiblePolicy;
import com.hotelbooking.domain.Hotel;
import com.hotelbooking.domain.Money;
import com.hotelbooking.domain.RoomType;
import com.hotelbooking.repository.BookingRepository;
import com.hotelbooking.repository.RoomInventoryRepository;
import com.hotelbooking.service.BookingService;
import com.hotelbooking.service.InventoryService;
import com.hotelbooking.service.PricingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        var invRepo = new RoomInventoryRepository();
        var bookingRepo = new BookingRepository();
        var inventory = new InventoryService(invRepo);
        var pricing = new PricingService(invRepo, new BigDecimal("0.12"));
        var booking = new BookingService(bookingRepo, inventory, pricing);

        Hotel hotel = new Hotel("Grand Hotel", "BLR");
        RoomType deluxe = new RoomType(hotel.id(), "Deluxe", 2);

        // seed 30 days of inventory: 2 rooms each at 1500 INR
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) {
            invRepo.seed(hotel.id(), deluxe.id(), today.plusDays(i), 2, Money.inr(1500));
        }

        UUID guest = UUID.randomUUID();

        // 1. Book 3 nights
        Booking b1 = booking.createBooking(
                guest, hotel.id(), deluxe.id(),
                today.plusDays(5), today.plusDays(8), 1,
                new FlexiblePolicy(), "key-1");
        System.out.println("Booked: " + b1);

        // 2. Idempotent retry
        Booking b1Retry = booking.createBooking(
                guest, hotel.id(), deluxe.id(),
                today.plusDays(5), today.plusDays(8), 1,
                new FlexiblePolicy(), "key-1");
        System.out.println("Idempotent same? " + b1.id().equals(b1Retry.id()));

        // 3. Concurrent contention: 2 attempts both want the last room on overlapping dates
        // Initially 2 rooms. b1 used 1; we book another 1. Both should succeed.
        Booking b2 = booking.createBooking(
                UUID.randomUUID(), hotel.id(), deluxe.id(),
                today.plusDays(5), today.plusDays(8), 1,
                new FlexiblePolicy(), "key-2");
        System.out.println("Booked second: " + b2);

        // 4. Now 0 rooms available on those nights; this must fail.
        try {
            booking.createBooking(
                    UUID.randomUUID(), hotel.id(), deluxe.id(),
                    today.plusDays(5), today.plusDays(8), 1,
                    new FlexiblePolicy(), "key-3");
            System.out.println("ERROR: oversold!");
        } catch (IllegalStateException e) {
            System.out.println("Expected: " + e.getMessage());
        }

        // 5. Cancel b1 (within free window? assume yes for demo)
        Booking cancelled = booking.cancel(b1.id(), Instant.now());
        System.out.println("Cancelled: " + cancelled);

        // 6. Now there's an opening, try to book 1 room for one of those nights
        Booking b3 = booking.createBooking(
                UUID.randomUUID(), hotel.id(), deluxe.id(),
                today.plusDays(5), today.plusDays(6), 1,
                new FlexiblePolicy(), "key-4");
        System.out.println("Booked after cancellation: " + b3);

        // 7. Print inventory snapshot for one date
        invRepo.find(hotel.id(), deluxe.id(), today.plusDays(5))
                .ifPresent(System.out::println);
    }
}
