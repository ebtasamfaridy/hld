package com.ridebooking.repository;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.DriverStatus;
import com.ridebooking.domain.RideType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class DriverRepository {
    private final ConcurrentMap<UUID, Driver> byId = new ConcurrentHashMap<>();

    public Optional<Driver> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Driver save(Driver d) { byId.put(d.id(), d); return d; }

    public List<Driver> findIdleByCityAndType(String city, RideType type) {
        return byId.values().stream()
                .filter(d -> d.city().equals(city)
                          && d.status() == DriverStatus.IDLE
                          && d.vehicle().supports() == type)
                .collect(Collectors.toList());
    }
}
