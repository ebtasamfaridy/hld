package com.splitwise.split;

import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShareSplit implements SplitStrategy {
    /** config: "shares" -> List<Integer> proportional weights. */
    @Override
    public List<ExpenseShare> compute(Money total, List<UUID> participants, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Integer> shares = (List<Integer>) config.get("shares");
        if (shares == null || shares.size() != participants.size())
            throw new IllegalArgumentException("shares size mismatch");
        long sumShares = shares.stream().mapToLong(Integer::longValue).sum();
        if (sumShares <= 0) throw new IllegalArgumentException("shares sum must be > 0");

        long totalCents = total.cents();
        long allocated = 0;
        List<ExpenseShare> out = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            long c;
            if (i == participants.size() - 1) {
                c = totalCents - allocated;
            } else {
                c = (totalCents * shares.get(i)) / sumShares;
                allocated += c;
            }
            out.add(new ExpenseShare(participants.get(i), Money.fromCents(c, total.currency())));
        }
        return out;
    }
}
