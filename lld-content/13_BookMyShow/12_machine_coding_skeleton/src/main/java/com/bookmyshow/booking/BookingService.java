package com.bookmyshow.booking;

import com.bookmyshow.domain.*;
import com.bookmyshow.inventory.SeatLock;
import com.bookmyshow.payment.PaymentService;
import com.bookmyshow.pricing.PricingPolicy;
import com.bookmyshow.repository.BookingRepository;
import com.bookmyshow.repository.HoldRepository;
import com.bookmyshow.repository.ShowRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class BookingService {

    private final ShowRepository showRepo;
    private final HoldRepository holdRepo;
    private final BookingRepository bookingRepo;
    private final SeatLock seatLock;
    private final PricingPolicy pricing;
    private final PaymentService payments;
    private final Clock clock;
    private final Duration holdTtl;

    public BookingService(ShowRepository showRepo, HoldRepository holdRepo,
                          BookingRepository bookingRepo, SeatLock seatLock,
                          PricingPolicy pricing, PaymentService payments,
                          Clock clock, Duration holdTtl) {
        this.showRepo = showRepo; this.holdRepo = holdRepo; this.bookingRepo = bookingRepo;
        this.seatLock = seatLock; this.pricing = pricing; this.payments = payments;
        this.clock = clock; this.holdTtl = holdTtl;
    }

    /** Acquire seat locks all-or-nothing; if any conflict, release the ones we won. */
    public HoldResult createHold(String userId, String showId, List<SeatId> seats) {
        Show show = showRepo.get(showId).orElseThrow();
        if (show.status() != ShowStatus.OPEN) return new HoldResult.ShowClosed(show.status().name());

        List<SeatId> acquired = new ArrayList<>();
        List<SeatId> conflicts = new ArrayList<>();
        for (SeatId s : seats) {
            if (seatLock.tryHold(showId, s, userId, holdTtl)) acquired.add(s);
            else conflicts.add(s);
        }
        if (!conflicts.isEmpty()) {
            for (SeatId s : acquired) seatLock.release(showId, s, userId);
            return new HoldResult.Conflict(conflicts);
        }

        int booked = bookingRepo.countConfirmedSeats(showId);
        PriceQuote quote = pricing.quote(show, seats, booked);
        Hold hold = new Hold(userId, showId, seats, quote,
                clock.instant(), clock.instant().plus(holdTtl));
        holdRepo.save(hold);
        return new HoldResult.Created(hold);
    }

    public ConfirmResult confirm(String holdId, String paymentToken, String idempotencyKey) {
        Hold hold = holdRepo.get(holdId).orElseThrow();

        if (!hold.isAlive(clock.instant())) {
            hold.markExpired();
            return new ConfirmResult.Expired();
        }

        // Charge first; if it fails, release seats.
        PaymentService.Result pay = payments.charge(hold.quote().total(), paymentToken, idempotencyKey);
        if (pay instanceof PaymentService.Result.Failure f) {
            for (SeatId s : hold.seats()) seatLock.release(hold.showId(), s, hold.userId());
            hold.markCancelled();
            return new ConfirmResult.PaymentFailed(f.reason());
        }
        String paymentRef = ((PaymentService.Result.Success) pay).paymentRef();

        // Build booking and atomically claim seats (Postgres PK analog).
        Show show = showRepo.get(hold.showId()).orElseThrow();
        List<BookedSeat> bookedSeats = new ArrayList<>();
        for (SeatId s : hold.seats()) {
            Seat seat = show.seats().get(s);
            bookedSeats.add(new BookedSeat(s, seat.category(), hold.quote().perSeat().get(s)));
        }
        Booking booking = new Booking(hold.userId(), hold.showId(), hold.id(),
                bookedSeats, hold.quote().total(), paymentRef, clock.instant());

        boolean stored = bookingRepo.trySaveAtomically(booking);
        if (!stored) {
            // Defense in depth: PK guard rejected. Refund.
            payments.refund(paymentRef, "refund:" + holdId);
            return new ConfirmResult.SeatConflict();
        }

        hold.markConfirmed(booking.id());
        for (SeatId s : hold.seats()) seatLock.release(hold.showId(), s, hold.userId());
        return new ConfirmResult.Confirmed(booking);
    }

    public void cancelHold(String holdId, String userId) {
        Hold h = holdRepo.get(holdId).orElseThrow();
        if (h.status() != Hold.Status.HELD) return;
        for (SeatId s : h.seats()) seatLock.release(h.showId(), s, userId);
        h.markCancelled();
    }
}
