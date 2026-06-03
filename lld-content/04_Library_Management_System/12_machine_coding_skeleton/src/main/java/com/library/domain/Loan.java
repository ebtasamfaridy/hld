package com.library.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Loan {
    private final UUID id;
    private final UUID memberId;
    private final UUID copyId;
    private final UUID issuedAtBranchId;
    private final Instant issuedAt;
    private LocalDate dueDate;
    private Instant returnedAt;
    private LoanStatus status = LoanStatus.BORROWED;
    private int renewals = 0;
    private long version;

    public Loan(UUID memberId, UUID copyId, UUID branchId, LocalDate dueDate) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.copyId = copyId;
        this.issuedAtBranchId = branchId;
        this.issuedAt = Instant.now();
        this.dueDate = dueDate;
    }

    public void renew(int days) {
        if (status != LoanStatus.BORROWED) throw new IllegalStateException("cannot renew " + status);
        if (renewals >= 1) throw new IllegalStateException("renewal limit reached");
        dueDate = dueDate.plusDays(days);
        renewals++;
        version++;
    }
    public void returned() {
        if (status != LoanStatus.BORROWED && status != LoanStatus.OVERDUE)
            throw new IllegalStateException("cannot return " + status);
        status = LoanStatus.RETURNED;
        returnedAt = Instant.now();
        version++;
    }
    public void markOverdue() {
        if (status == LoanStatus.BORROWED) { status = LoanStatus.OVERDUE; version++; }
    }
    public void markLost() { status = LoanStatus.LOST; version++; }
    public void markDamaged() { status = LoanStatus.DAMAGED; version++; }

    public UUID id() { return id; }
    public UUID memberId() { return memberId; }
    public UUID copyId() { return copyId; }
    public UUID issuedAtBranchId() { return issuedAtBranchId; }
    public LocalDate dueDate() { return dueDate; }
    public LoanStatus status() { return status; }
    public Instant returnedAt() { return returnedAt; }
    public int renewals() { return renewals; }
    public Instant issuedAt() { return issuedAt; }

    @Override public String toString() {
        return "Loan{" + id.toString().substring(0,8) + " " + status + " due=" + dueDate + "}";
    }
}
