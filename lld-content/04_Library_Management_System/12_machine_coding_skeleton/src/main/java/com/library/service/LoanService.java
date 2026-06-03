package com.library.service;

import com.library.domain.BookCopy;
import com.library.domain.CopyStatus;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.policy.BorrowPolicy;
import com.library.repository.BookCopyRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class LoanService {
    private final LoanRepository loans;
    private final BookCopyRepository copies;
    private final MemberRepository members;
    private final BorrowPolicy policy;
    private final ReservationService reservations;

    public LoanService(LoanRepository loans, BookCopyRepository copies, MemberRepository members,
                       BorrowPolicy policy, ReservationService reservations) {
        this.loans = loans;
        this.copies = copies;
        this.members = members;
        this.policy = policy;
        this.reservations = reservations;
    }

    public Optional<Loan> borrow(UUID memberId, UUID bookId, UUID branchId) {
        Member m = members.findById(memberId).orElseThrow();
        if (!m.active()) throw new IllegalStateException("ACCOUNT_SUSPENDED");
        if (m.activeLoanCount() >= policy.maxActiveLoans()) throw new IllegalStateException("LIMIT_REACHED");
        if (m.outstandingFines().isPositive()) throw new IllegalStateException("OUTSTANDING_FINES");

        // try to find an available copy
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<BookCopy> avail = copies.findFirstAvailable(bookId, branchId);
            if (avail.isEmpty()) return Optional.empty();
            BookCopy c = avail.get();
            boolean ok = copies.trySetStatusCAS(c.id(), CopyStatus.AVAILABLE,
                                                CopyStatus.BORROWED, c.version());
            if (ok) {
                m.incrementLoans(); members.save(m);
                Loan loan = new Loan(memberId, c.id(), branchId,
                                     LocalDate.now().plusDays(policy.loanPeriodDays()));
                return Optional.of(loans.save(loan));
            }
            // retry — another transaction took it
        }
        return Optional.empty();
    }

    public Loan returnLoan(UUID loanId) {
        Loan l = loans.findById(loanId).orElseThrow();
        BookCopy c = copies.findById(l.copyId()).orElseThrow();

        l.returned();
        loans.save(l);

        // If a reservation exists, hold for it; else available
        UUID promotedReservationId = reservations.tryPromote(c.bookId(), c.id(), c.version());
        if (promotedReservationId == null) {
            copies.trySetStatusCAS(c.id(), CopyStatus.BORROWED, CopyStatus.AVAILABLE, c.version());
        } else {
            // reservations.tryPromote already CAS'd to RESERVED_HOLD
        }

        Member m = members.findById(l.memberId()).orElseThrow();
        m.decrementLoans(); members.save(m);
        return l;
    }

    public Loan renew(UUID loanId, int days) {
        Loan l = loans.findById(loanId).orElseThrow();
        // reject if reservations exist for this book
        if (reservations.hasQueued(copyBookId(l.copyId()))) {
            throw new IllegalStateException("RESERVATION_EXISTS");
        }
        l.renew(days);
        return loans.save(l);
    }

    private UUID copyBookId(UUID copyId) {
        return copies.findById(copyId).orElseThrow().bookId();
    }
}
