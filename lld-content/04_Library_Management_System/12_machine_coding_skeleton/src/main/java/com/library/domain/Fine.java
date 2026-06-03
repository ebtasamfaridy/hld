package com.library.domain;

import java.time.Instant;
import java.util.UUID;

public final class Fine {
    public enum Kind { LATE, LOST, DAMAGED }
    public enum Status { OUTSTANDING, PAID, WAIVED }

    private final UUID id;
    private final UUID memberId;
    private final UUID loanId;
    private final Kind kind;
    private Money amount;
    private Money paidAmount;
    private Status status = Status.OUTSTANDING;
    private Instant createdAt = Instant.now();

    public Fine(UUID memberId, UUID loanId, Kind kind, Money amount) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.loanId = loanId;
        this.kind = kind;
        this.amount = amount;
        this.paidAmount = Money.zero(amount.currency());
    }

    public void pay() {
        if (status != Status.OUTSTANDING) throw new IllegalStateException();
        paidAmount = amount;
        status = Status.PAID;
    }
    public void waive() {
        if (status != Status.OUTSTANDING) throw new IllegalStateException();
        status = Status.WAIVED;
    }
    public void increment(Money m) {
        if (status != Status.OUTSTANDING) throw new IllegalStateException();
        amount = amount.add(m);
    }

    public UUID id() { return id; }
    public UUID memberId() { return memberId; }
    public UUID loanId() { return loanId; }
    public Kind kind() { return kind; }
    public Money amount() { return amount; }
    public Money paidAmount() { return paidAmount; }
    public Status status() { return status; }

    @Override public String toString() {
        return "Fine{" + kind + " " + amount + " " + status + "}";
    }
}
