package com.fooddelivery.repository;

import com.fooddelivery.domain.DeliveryAssignment;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AssignmentRepository {
    private final ConcurrentMap<UUID, DeliveryAssignment> byId = new ConcurrentHashMap<>();

    public Optional<DeliveryAssignment> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public DeliveryAssignment save(DeliveryAssignment a) {
        byId.put(a.id(), a);
        return a;
    }
}
