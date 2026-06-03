package com.library.repository;

import com.library.domain.Loan;
import com.library.domain.LoanStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class LoanRepository {
    private final ConcurrentMap<UUID, Loan> byId = new ConcurrentHashMap<>();

    public Loan save(Loan l) { byId.put(l.id(), l); return l; }
    public Optional<Loan> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<Loan> findActiveForCopy(UUID copyId) {
        return byId.values().stream()
                .filter(l -> l.copyId().equals(copyId)
                          && (l.status() == LoanStatus.BORROWED || l.status() == LoanStatus.OVERDUE))
                .findFirst();
    }
    public List<Loan> findOverdueAsOf(java.time.LocalDate today) {
        return byId.values().stream()
                .filter(l -> l.status() == LoanStatus.BORROWED && l.dueDate().isBefore(today))
                .collect(Collectors.toList());
    }
}
