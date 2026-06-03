package com.splitwise.domain;

import java.util.UUID;

public final class Payer {
    private final UUID userId;
    private final Money amount;
    public Payer(UUID userId, Money amount) { this.userId = userId; this.amount = amount; }
    public UUID userId() { return userId; }
    public Money amount() { return amount; }
    @Override public String toString() { return userId.toString().substring(0,8) + "=" + amount; }
}
