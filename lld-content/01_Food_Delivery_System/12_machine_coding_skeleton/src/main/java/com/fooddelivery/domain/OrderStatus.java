package com.fooddelivery.domain;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    REJECTED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        PLACED,           Set.of(CONFIRMED, REJECTED, CANCELLED),
        CONFIRMED,        Set.of(PREPARING, CANCELLED),
        PREPARING,        Set.of(READY_FOR_PICKUP),
        READY_FOR_PICKUP, Set.of(OUT_FOR_DELIVERY),
        OUT_FOR_DELIVERY, Set.of(DELIVERED),
        DELIVERED,        Set.of(),
        CANCELLED,        Set.of(),
        REJECTED,         Set.of()
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (!from.canTransitionTo(to))
            throw new IllegalStateException("Illegal transition: " + from + " -> " + to);
    }
}
