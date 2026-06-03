package com.library.service;

import com.library.domain.Fine;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.Money;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FineService {
    private final ConcurrentMap<UUID, Fine> fines = new ConcurrentHashMap<>();
    private final LoanRepository loans;
    private final MemberRepository members;
    private final Money perDayLate;

    public FineService(LoanRepository loans, MemberRepository members, Money perDayLate) {
        this.loans = loans;
        this.members = members;
        this.perDayLate = perDayLate;
    }

    /** Idempotent daily run: ensures one LATE fine per (loan, today). */
    public void accrueDailyOverdues(LocalDate today) {
        List<Loan> overdue = loans.findOverdueAsOf(today);
        for (Loan l : overdue) {
            l.markOverdue();
            loans.save(l);

            long daysLate = ChronoUnit.DAYS.between(l.dueDate(), today);
            Money fineAmount = perDayLate.multiply(daysLate);

            // find existing OUTSTANDING LATE fine for this loan
            Fine existing = fines.values().stream()
                    .filter(f -> f.loanId().equals(l.id())
                              && f.kind() == Fine.Kind.LATE
                              && f.status() == Fine.Status.OUTSTANDING)
                    .findFirst().orElse(null);

            Money delta;
            if (existing == null) {
                Fine f = new Fine(l.memberId(), l.id(), Fine.Kind.LATE, fineAmount);
                fines.put(f.id(), f);
                delta = fineAmount;
            } else {
                Money diff = fineAmount.subtract(existing.amount());
                if (diff.isPositive()) {
                    existing.increment(diff);
                    delta = diff;
                } else {
                    delta = Money.zero(fineAmount.currency());
                }
            }
            Member m = members.findById(l.memberId()).orElseThrow();
            if (delta.isPositive()) { m.addFine(delta); members.save(m); }
        }
    }

    public Fine pay(UUID fineId) {
        Fine f = fines.get(fineId);
        if (f == null) throw new IllegalArgumentException("not found");
        Member m = members.findById(f.memberId()).orElseThrow();
        f.pay();
        m.payFine(f.amount());
        members.save(m);
        return f;
    }

    public List<Fine> finesFor(UUID memberId) {
        return fines.values().stream().filter(f -> f.memberId().equals(memberId)).toList();
    }
}
