package com.vending.payment;

import com.vending.domain.Denomination;
import com.vending.domain.Money;

import java.util.Map;
import java.util.Optional;

public interface ChangeMaker {
    Optional<Map<Denomination, Integer>> makeChange(Money amount, Map<Denomination, Integer> available);
}
