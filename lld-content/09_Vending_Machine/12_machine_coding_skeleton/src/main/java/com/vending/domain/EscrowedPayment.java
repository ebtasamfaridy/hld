package com.vending.domain;

import java.util.HashMap;
import java.util.Map;

public final class EscrowedPayment {
    private final Map<Denomination, Integer> coins = new HashMap<>();
    private Money total = Money.inr(0);

    public void add(Denomination d) {
        coins.merge(d, 1, Integer::sum);
        total = total.plus(d.value());
    }

    public Map<Denomination, Integer> snapshotCoins() { return Map.copyOf(coins); }
    public Money total() { return total; }
    public boolean isEmpty() { return total.isZero(); }

    public void clear() { coins.clear(); total = Money.inr(0); }
}
