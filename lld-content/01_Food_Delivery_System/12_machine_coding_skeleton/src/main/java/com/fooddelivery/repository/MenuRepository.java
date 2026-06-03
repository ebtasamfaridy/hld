package com.fooddelivery.repository;

import com.fooddelivery.domain.MenuItem;
import com.fooddelivery.domain.Restaurant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class MenuRepository {
    private final ConcurrentMap<UUID, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MenuItem> items = new ConcurrentHashMap<>();

    public Restaurant save(Restaurant r) {
        restaurants.put(r.id(), r);
        return r;
    }

    public MenuItem save(MenuItem m) {
        items.put(m.id(), m);
        return m;
    }

    public Optional<Restaurant> findRestaurant(UUID id) {
        return Optional.ofNullable(restaurants.get(id));
    }

    public Optional<MenuItem> findItem(UUID id) {
        return Optional.ofNullable(items.get(id));
    }

    public List<MenuItem> menuFor(UUID restaurantId) {
        return items.values().stream()
                .filter(i -> i.restaurantId().equals(restaurantId) && i.isAvailable())
                .collect(Collectors.toList());
    }
}
