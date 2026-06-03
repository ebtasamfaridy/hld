package com.splitwise.split;

import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExactSplit implements SplitStrategy {
    /** config: "amounts" -> List<Money> aligned with participants */
    @Override
    public List<ExpenseShare> compute(Money total, List<UUID> participants, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Money> amounts = (List<Money>) config.get("amounts");
        if (amounts == null || amounts.size() != participants.size())
            throw new IllegalArgumentException("amounts size mismatch");

        long sum = amounts.stream().mapToLong(Money::cents).sum();
        if (sum != total.cents()) throw new IllegalArgumentException("amounts don't sum to total");

        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            shares.add(new ExpenseShare(participants.get(i), amounts.get(i)));
        }
        return shares;
    }
}
