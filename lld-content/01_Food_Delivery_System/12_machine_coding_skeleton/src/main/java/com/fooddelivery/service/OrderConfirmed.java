package com.fooddelivery.service;

import java.time.Instant;
import java.util.UUID;

public final class OrderConfirmed {
    public final UUID orderId;
    public final Instant at;
    public OrderConfirmed(UUID orderId) { this.orderId = orderId; this.at = Instant.now(); }
    @Override public String toString() { return "OrderConfirmed{" + orderId + "}"; }
}
