package com.splitwise.simplify;

import com.splitwise.domain.Money;

import java.util.UUID;

public final class Transfer {
    private final UUID from;
    private final UUID to;
    private final Money amount;
    public Transfer(UUID from, UUID to, Money amount) { this.from = from; this.to = to; this.amount = amount; }
    public UUID from() { return from; }
    public UUID to() { return to; }
    public Money amount() { return amount; }
    @Override public String toString() {
        return from.toString().substring(0,8) + " -> " + to.toString().substring(0,8) + " : " + amount;
    }
}
