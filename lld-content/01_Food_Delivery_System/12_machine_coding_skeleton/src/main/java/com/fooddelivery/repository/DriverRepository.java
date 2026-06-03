package com.fooddelivery.repository;

import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.DriverStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository {
    Optional<Driver> findById(UUID id);
    List<Driver> findByCityAndStatus(String city, DriverStatus status);
    Driver save(Driver driver);
}
