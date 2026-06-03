package com.fooddelivery.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Order {
    private final UUID id;
    private final UUID customerId;
    private final UUID restaurantId;
    private final List<OrderItem> items;
    private final PriceBreakdown priceBreakdown;
    private final String idempotencyKey;
    private final Instant createdAt;

    private OrderStatus status;
    private UUID assignmentId;
    private long version;

    private Order(Builder b) {
        if (b.items.isEmpty()) throw new IllegalArgumentException("at least one item required");
        this.id = b.id;
        this.customerId = b.customerId;
        this.restaurantId = b.restaurantId;
        this.items = List.copyOf(b.items);
        this.priceBreakdown = b.priceBreakdown;
        this.idempotencyKey = b.idempotencyKey;
        this.status = OrderStatus.PLACED;
        this.createdAt = Instant.now();
        this.version = 0;
    }

    public static Builder builder() { return new Builder(); }

    public void confirm()         { transition(OrderStatus.CONFIRMED); }
    public void reject()          { transition(OrderStatus.REJECTED); }
    public void cancel()          { transition(OrderStatus.CANCELLED); }
    public void markPreparing()   { transition(OrderStatus.PREPARING); }
    public void markReady()       { transition(OrderStatus.READY_FOR_PICKUP); }
    public void markOutForDelivery(UUID assignmentId) {
        transition(OrderStatus.OUT_FOR_DELIVERY);
        this.assignmentId = assignmentId;
    }
    public void markDelivered()   { transition(OrderStatus.DELIVERED); }

    public boolean canCancel() {
        return status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED;
    }

    private void transition(OrderStatus next) {
        OrderStatus.requireTransition(status, next);
        status = next;
        version++;
    }

    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public UUID restaurantId() { return restaurantId; }
    public List<OrderItem> items() { return Collections.unmodifiableList(items); }
    public OrderStatus status() { return status; }
    public PriceBreakdown priceBreakdown() { return priceBreakdown; }
    public String idempotencyKey() { return idempotencyKey; }
    public UUID assignmentId() { return assignmentId; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }

    @Override public String toString() {
        return "Order{id=" + id + ", status=" + status + ", total="
             + priceBreakdown.total() + ", v=" + version + "}";
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private UUID customerId;
        private UUID restaurantId;
        private final List<OrderItem> items = new ArrayList<>();
        private PriceBreakdown priceBreakdown;
        private String idempotencyKey;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder customer(UUID id) { this.customerId = id; return this; }
        public Builder restaurant(UUID id) { this.restaurantId = id; return this; }
        public Builder addItem(OrderItem i) { this.items.add(i); return this; }
        public Builder priceBreakdown(PriceBreakdown p) { this.priceBreakdown = p; return this; }
        public Builder idempotencyKey(String k) { this.idempotencyKey = k; return this; }

        public Order build() {
            if (customerId == null)   throw new IllegalStateException("customer required");
            if (restaurantId == null) throw new IllegalStateException("restaurant required");
            if (priceBreakdown == null) throw new IllegalStateException("price required");
            return new Order(this);
        }
    }
}
