package com.fooddelivery.repository;

import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.DriverStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class InMemoryDriverRepository implements DriverRepository {
    private final ConcurrentMap<UUID, Driver> byId = new ConcurrentHashMap<>();

    @Override public Optional<Driver> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override public List<Driver> findByCityAndStatus(String city, DriverStatus status) {
        return byId.values().stream()
                .filter(d -> d.city().equals(city) && d.status() == status)
                .collect(Collectors.toList());
    }

    @Override public Driver save(Driver driver) {
        byId.put(driver.id(), driver);
        return driver;
    }
}
