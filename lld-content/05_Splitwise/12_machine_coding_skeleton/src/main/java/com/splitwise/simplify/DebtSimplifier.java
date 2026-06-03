package com.splitwise.simplify;

import com.splitwise.domain.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

public final class DebtSimplifier {

    /**
     * Min-cash-flow heuristic.
     * Input: net balance per user (positive=should receive, negative=should pay), all in same currency.
     * Output: list of transfers minimizing cash movement.
     */
    public List<Transfer> simplify(Map<UUID, Long> netCents, String currency) {
        record E(UUID user, long amount) {}
        PriorityQueue<E> debtors  = new PriorityQueue<>(Comparator.comparingLong(E::amount));            // most negative first
        PriorityQueue<E> creditors = new PriorityQueue<>(Comparator.comparingLong((E e) -> e.amount()).reversed()); // most positive first

        for (var en : netCents.entrySet()) {
            if (en.getValue() < 0) debtors.add(new E(en.getKey(), en.getValue()));
            else if (en.getValue() > 0) creditors.add(new E(en.getKey(), en.getValue()));
        }

        List<Transfer> out = new ArrayList<>();
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            E d = debtors.poll();
            E c = creditors.poll();
            long pay = Math.min(-d.amount(), c.amount());
            out.add(new Transfer(d.user(), c.user(), Money.fromCents(pay, currency)));
            long dRem = d.amount() + pay;
            long cRem = c.amount() - pay;
            if (dRem < 0) debtors.add(new E(d.user(), dRem));
            if (cRem > 0) creditors.add(new E(c.user(), cRem));
        }
        return out;
    }
}
