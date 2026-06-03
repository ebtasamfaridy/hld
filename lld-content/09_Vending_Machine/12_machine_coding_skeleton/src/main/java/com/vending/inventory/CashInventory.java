package com.vending.inventory;

import com.vending.domain.Denomination;
import com.vending.domain.Money;

import java.util.HashMap;
import java.util.Map;

public final class CashInventory {
    private final Map<Denomination, Integer> counts = new HashMap<>();

    public void add(Denomination d, int n) {
        counts.merge(d, n, Integer::sum);
    }

    public void addAll(Map<Denomination, Integer> map) {
        map.forEach(this::add);
    }

    public boolean removeAll(Map<Denomination, Integer> map) {
        // Validate first; remove only if all available.
        for (var e : map.entrySet()) {
            if (counts.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        for (var e : map.entrySet()) {
            counts.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
        return true;
    }

    public int countOf(Denomination d) { return counts.getOrDefault(d, 0); }

    public Map<Denomination, Integer> snapshot() { return Map.copyOf(counts); }

    public Money totalValue() {
        Money total = Money.inr(0);
        for (var e : counts.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) total = total.plus(e.getKey().value());
        }
        return total;
    }

    public Money drainAll() {
        Money total = totalValue();
        counts.clear();
        return total;
    }
}
