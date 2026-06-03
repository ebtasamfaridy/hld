package com.library.service;

import com.library.domain.BookCopy;
import com.library.domain.CopyStatus;
import com.library.domain.Reservation;
import com.library.policy.BorrowPolicy;
import com.library.repository.BookCopyRepository;
import com.library.repository.ReservationRepository;

import java.util.Optional;
import java.util.UUID;

public final class ReservationService {
    private final ReservationRepository reservations;
    private final BookCopyRepository copies;
    private final BorrowPolicy policy;

    public ReservationService(ReservationRepository reservations, BookCopyRepository copies,
                              BorrowPolicy policy) {
        this.reservations = reservations;
        this.copies = copies;
        this.policy = policy;
    }

    public Reservation reserve(UUID memberId, UUID bookId) {
        int pos = reservations.nextQueuePosition(bookId);
        return reservations.save(new Reservation(memberId, bookId, pos));
    }

    public boolean hasQueued(UUID bookId) {
        return reservations.findHeadOfQueue(bookId).isPresent();
    }

    /**
     * Try to promote head-of-queue, holding the given copy.
     * Returns reservation id if promoted, null otherwise.
     */
    public UUID tryPromote(UUID bookId, UUID copyId, long copyVersion) {
        Optional<Reservation> head = reservations.findHeadOfQueue(bookId);
        if (head.isEmpty()) return null;
        boolean ok = copies.trySetStatusCAS(copyId, CopyStatus.BORROWED, CopyStatus.RESERVED_HOLD, copyVersion);
        if (!ok) return null;
        Reservation r = head.get();
        r.promote(copyId, policy.reservationHoldHours());
        reservations.save(r);
        return r.id();
    }

    /** Member borrows the held copy; transitions reservation to FULFILLED. */
    public BookCopy claimHeld(UUID reservationId) {
        Reservation r = reservations.findById(reservationId).orElseThrow();
        if (r.status() != Reservation.Status.READY) throw new IllegalStateException();
        BookCopy c = copies.findById(r.heldCopyId()).orElseThrow();
        boolean ok = copies.trySetStatusCAS(c.id(), CopyStatus.RESERVED_HOLD, CopyStatus.BORROWED, c.version());
        if (!ok) throw new IllegalStateException("hold lost");
        r.fulfill();
        reservations.save(r);
        return c;
    }
}
