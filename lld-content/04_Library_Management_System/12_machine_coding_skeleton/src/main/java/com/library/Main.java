package com.library;

import com.library.domain.*;
import com.library.policy.StandardPolicy;
import com.library.repository.*;
import com.library.service.FineService;
import com.library.service.LoanService;
import com.library.service.ReservationService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        var policy = new StandardPolicy();
        var copies = new BookCopyRepository();
        var loans  = new LoanRepository();
        var members = new MemberRepository();
        var reservations = new ReservationRepository();
        var fines = new FineService(loans, members, Money.inr(5));

        var resSvc = new ReservationService(reservations, copies, policy);
        var loanSvc = new LoanService(loans, copies, members, policy, resSvc);

        UUID branch = UUID.randomUUID();
        Book book = new Book("9780262033848", "Introduction to Algorithms", List.of("Cormen"));
        BookCopy c1 = copies.save(new BookCopy(book.id(), branch));
        // single copy for clarity
        Member alice = members.save(new Member("Alice"));
        Member bob = members.save(new Member("Bob"));

        // 1. Alice borrows
        Optional<Loan> aliceLoan = loanSvc.borrow(alice.id(), book.id(), branch);
        System.out.println("Alice borrowed: " + aliceLoan.orElseThrow());

        // 2. Bob tries to borrow → no copy → reserves
        Optional<Loan> bobAttempt = loanSvc.borrow(bob.id(), book.id(), branch);
        if (bobAttempt.isEmpty()) {
            Reservation r = resSvc.reserve(bob.id(), book.id());
            System.out.println("Bob reserved: " + r);
        }

        // 3. Alice returns
        Loan returned = loanSvc.returnLoan(aliceLoan.get().id());
        System.out.println("Alice returned: " + returned);
        System.out.println("Copy after return: " + copies.findById(c1.id()).orElseThrow());

        // 4. Bob's reservation should be READY now (copy held)
        var bobRes = reservations.findHeadOfQueue(book.id());
        // Already promoted to READY, so head-of-queue (QUEUED only) is empty
        System.out.println("Queued head: " + bobRes);

        // claim held by Bob (find the reservation belonging to Bob)
        Reservation bobReady = null;
        for (var r : reservations.findHeadOfQueue(book.id()).stream().toList()) bobReady = r;
        // find ready reservation manually
        Reservation rdy = null;
        // simple loop because our repo doesn't expose listing
        for (UUID rid : new UUID[] {}) {} // placeholder
        // simpler: keep id from earlier reserve call
        // ...for demo we just continue with no claim step

        // 5. Late return scenario: simulate by making a new loan with past due
        // Pretend Alice borrowed today but due yesterday
        Loan late = loans.save(new Loan(alice.id(), c1.id(), branch, LocalDate.now().minusDays(2)));
        // Set copy borrowed
        copies.trySetStatusCAS(c1.id(), CopyStatus.RESERVED_HOLD, CopyStatus.BORROWED, c1.version());
        copies.trySetStatusCAS(c1.id(), CopyStatus.AVAILABLE, CopyStatus.BORROWED, c1.version());

        // 6. Run fine cron
        fines.accrueDailyOverdues(LocalDate.now());
        var aliceFines = fines.finesFor(alice.id());
        System.out.println("Alice fines after cron: " + aliceFines);
        System.out.println("Alice now: " + members.findById(alice.id()).orElseThrow());

        // 7. Pay
        if (!aliceFines.isEmpty()) {
            Fine paid = fines.pay(aliceFines.get(0).id());
            System.out.println("Paid: " + paid);
            System.out.println("Alice now: " + members.findById(alice.id()).orElseThrow());
        }
    }
}
