package com.splitwise.split;

import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EqualSplit implements SplitStrategy {
    @Override
    public List<ExpenseShare> compute(Money total, List<UUID> participants, Map<String, Object> config) {
        if (participants.isEmpty()) throw new IllegalArgumentException("no participants");
        long totalCents = total.cents();
        int n = participants.size();
        long perEach = totalCents / n;
        long remainder = totalCents - perEach * n;   // distribute these remainders one cent each
        List<ExpenseShare> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long c = perEach + (i < remainder ? 1 : 0);
            shares.add(new ExpenseShare(participants.get(i), Money.fromCents(c, total.currency())));
        }
        return shares;
    }
}
