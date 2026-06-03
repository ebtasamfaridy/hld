package com.fooddelivery.repository;

import com.fooddelivery.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Optional<Order> findById(UUID id);
    Optional<Order> findByIdempotencyKey(String key);
    Order save(Order order);
}
