package com.fooddelivery.repository;

import com.fooddelivery.domain.Order;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final ConcurrentMap<UUID, Order> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> byIdempotency = new ConcurrentHashMap<>();

    @Override public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override public Optional<Order> findByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        UUID id = byIdempotency.get(key);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Save with idempotency-key uniqueness enforced atomically.
     * If another order already exists with the same key, returns the existing one.
     */
    @Override public Order save(Order order) {
        if (order.idempotencyKey() != null) {
            UUID existingId = byIdempotency.putIfAbsent(order.idempotencyKey(), order.id());
            if (existingId != null && !existingId.equals(order.id())) {
                return byId.get(existingId);
            }
        }
        byId.put(order.id(), order);
        return order;
    }
}
