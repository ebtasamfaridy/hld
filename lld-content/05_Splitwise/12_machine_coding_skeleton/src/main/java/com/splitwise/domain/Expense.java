package com.splitwise.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Expense {
    public enum Status { ACTIVE, EDITED, DELETED }

    private final UUID id;
    private final UUID groupId;       // optional
    private final UUID createdBy;
    private final String description;
    private final Money amount;
    private final SplitMethod method;
    private final List<Payer> payers;
    private final List<ExpenseShare> shares;
    private final Instant occurredAt;
    private final String idempotencyKey;
    private Status status = Status.ACTIVE;
    private long version = 1;

    public Expense(UUID groupId, UUID createdBy, String description, Money amount,
                   SplitMethod method, List<Payer> payers, List<ExpenseShare> shares,
                   Instant occurredAt, String idempotencyKey) {
        validate(amount, payers, shares);
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.createdBy = createdBy;
        this.description = description;
        this.amount = amount;
        this.method = method;
        this.payers = List.copyOf(payers);
        this.shares = List.copyOf(shares);
        this.occurredAt = occurredAt;
        this.idempotencyKey = idempotencyKey;
    }

    private static void validate(Money amount, List<Payer> payers, List<ExpenseShare> shares) {
        long sumPaid = payers.stream().mapToLong(p -> p.amount().cents()).sum();
        long sumOwed = shares.stream().mapToLong(s -> s.owedAmount().cents()).sum();
        if (sumPaid != amount.cents()) throw new IllegalArgumentException("payers sum != total");
        if (sumOwed != amount.cents()) throw new IllegalArgumentException("shares sum != total");
        // currency consistency
        for (var p : payers) if (!p.amount().currency().equals(amount.currency())) throw new IllegalArgumentException("currency");
        for (var s : shares) if (!s.owedAmount().currency().equals(amount.currency())) throw new IllegalArgumentException("currency");
    }

    public void markEdited() { status = Status.EDITED; version++; }
    public void delete() { status = Status.DELETED; version++; }

    public UUID id() { return id; }
    public UUID groupId() { return groupId; }
    public UUID createdBy() { return createdBy; }
    public String description() { return description; }
    public Money amount() { return amount; }
    public SplitMethod method() { return method; }
    public List<Payer> payers() { return payers; }
    public List<ExpenseShare> shares() { return shares; }
    public Instant occurredAt() { return occurredAt; }
    public Status status() { return status; }
    public long version() { return version; }
    public String idempotencyKey() { return idempotencyKey; }

    @Override public String toString() {
        return "Expense{" + description + " " + amount + " by " + payers + " split=" + method + "}";
    }
}
