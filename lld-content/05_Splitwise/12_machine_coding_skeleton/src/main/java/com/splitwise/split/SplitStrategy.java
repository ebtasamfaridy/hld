package com.splitwise.split;

import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Money;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SplitStrategy {
    List<ExpenseShare> compute(Money total, List<UUID> participants, Map<String, Object> config);
}
