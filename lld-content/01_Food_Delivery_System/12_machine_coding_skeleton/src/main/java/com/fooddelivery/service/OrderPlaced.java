package com.fooddelivery.service;

import java.time.Instant;
import java.util.UUID;

public final class OrderPlaced {
    public final UUID orderId;
    public final Instant at;
    public OrderPlaced(UUID orderId) { this.orderId = orderId; this.at = Instant.now(); }
    @Override public String toString() { return "OrderPlaced{" + orderId + "}"; }
}
