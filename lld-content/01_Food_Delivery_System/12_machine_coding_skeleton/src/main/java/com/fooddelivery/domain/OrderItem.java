package com.fooddelivery.domain;

import java.util.UUID;

public final class OrderItem {
    private final UUID id;
    private final UUID menuItemId;
    private final String nameSnapshot;
    private final int quantity;
    private final Money unitPriceSnapshot;

    public OrderItem(UUID menuItemId, String nameSnapshot, int quantity, Money unitPriceSnapshot) {
        if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");
        this.id = UUID.randomUUID();
        this.menuItemId = menuItemId;
        this.nameSnapshot = nameSnapshot;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public Money lineTotal() {
        return unitPriceSnapshot.multiply((double) quantity);
    }

    public UUID id() { return id; }
    public UUID menuItemId() { return menuItemId; }
    public String nameSnapshot() { return nameSnapshot; }
    public int quantity() { return quantity; }
    public Money unitPriceSnapshot() { return unitPriceSnapshot; }

    @Override public String toString() {
        return quantity + "x " + nameSnapshot + " @ " + unitPriceSnapshot;
    }
}
