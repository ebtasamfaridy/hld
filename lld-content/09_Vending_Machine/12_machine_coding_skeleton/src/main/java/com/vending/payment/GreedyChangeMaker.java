package com.vending.payment;

import com.vending.domain.Denomination;
import com.vending.domain.Money;

import java.util.*;

/**
 * Greedy by largest denomination. Correct for canonical denominations
 * (Indian / US / Eurozone). Otherwise use DpChangeMaker.
 */
public final class GreedyChangeMaker implements ChangeMaker {

    @Override
    public Optional<Map<Denomination, Integer>> makeChange(Money amount, Map<Denomination, Integer> available) {
        if (amount.isZero()) return Optional.of(Map.of());

        List<Denomination> sorted = new ArrayList<>(available.keySet());
        sorted.sort(Comparator.reverseOrder());

        Map<Denomination, Integer> picked = new HashMap<>();
        Money remaining = amount;

        for (Denomination d : sorted) {
            if (remaining.isZero()) break;
            long denomValue = d.value().minor();
            int avail = available.getOrDefault(d, 0);
            long needCount = remaining.minor() / denomValue;
            int take = (int) Math.min(needCount, avail);
            if (take > 0) {
                picked.put(d, take);
                long taken = take * denomValue;
                remaining = new Money(remaining.minor() - taken, remaining.currency());
            }
        }
        return remaining.isZero() ? Optional.of(picked) : Optional.empty();
    }
}
