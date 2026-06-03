package com.fooddelivery.service;

import com.fooddelivery.domain.Money;

import java.util.List;
import java.util.UUID;

public final class PlaceOrderCommand {
    public final UUID customerId;
    public final UUID restaurantId;
    public final List<LineItem> items;
    public final String idempotencyKey;
    public final String currency;

    public PlaceOrderCommand(UUID customerId, UUID restaurantId,
                             List<LineItem> items, String idempotencyKey, String currency) {
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.items = List.copyOf(items);
        this.idempotencyKey = idempotencyKey;
        this.currency = currency;
    }

    public static final class LineItem {
        public final UUID menuItemId;
        public final int quantity;
        public LineItem(UUID menuItemId, int quantity) {
            if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");
            this.menuItemId = menuItemId;
            this.quantity = quantity;
        }
    }
}
