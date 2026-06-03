package com.splitwise.split;

import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PercentSplit implements SplitStrategy {
    /** config: "percents" -> List<BigDecimal>, must sum to 100. */
    @Override
    public List<ExpenseShare> compute(Money total, List<UUID> participants, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<BigDecimal> percents = (List<BigDecimal>) config.get("percents");
        if (percents == null || percents.size() != participants.size())
            throw new IllegalArgumentException("percents size mismatch");

        BigDecimal sumPct = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumPct.compareTo(BigDecimal.valueOf(100)) != 0)
            throw new IllegalArgumentException("percents must sum to 100, got " + sumPct);

        long totalCents = total.cents();
        long allocated = 0;
        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            long c;
            if (i == participants.size() - 1) {
                c = totalCents - allocated;       // last gets remainder for exact sum
            } else {
                c = percents.get(i).movePointRight(2).multiply(BigDecimal.valueOf(totalCents))
                        .movePointLeft(4).longValue();
                allocated += c;
            }
            shares.add(new ExpenseShare(participants.get(i), Money.fromCents(c, total.currency())));
        }
        return shares;
    }
}
