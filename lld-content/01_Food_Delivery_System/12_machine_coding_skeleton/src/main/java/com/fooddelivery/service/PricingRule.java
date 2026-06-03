package com.fooddelivery.service;

import com.fooddelivery.domain.Money;
import com.fooddelivery.domain.OrderItem;

import java.util.List;

@FunctionalInterface
public interface PricingRule {
    /** Mutate the breakdown given the cart items. */
    void apply(List<OrderItem> items, PricingService.Builder b);
}
