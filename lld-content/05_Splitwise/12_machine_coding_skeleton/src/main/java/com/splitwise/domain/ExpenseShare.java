package com.splitwise.domain;

import java.util.UUID;

public final class ExpenseShare {
    private final UUID userId;
    private final Money owedAmount;

    public ExpenseShare(UUID userId, Money owedAmount) {
        this.userId = userId;
        this.owedAmount = owedAmount;
    }
    public UUID userId() { return userId; }
    public Money owedAmount() { return owedAmount; }
    @Override public String toString() { return userId.toString().substring(0,8) + "→" + owedAmount; }
}
