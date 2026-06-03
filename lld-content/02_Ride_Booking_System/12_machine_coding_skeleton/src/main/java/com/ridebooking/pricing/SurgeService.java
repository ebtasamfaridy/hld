package com.ridebooking.pricing;

import com.ridebooking.domain.Location;
import com.ridebooking.domain.RideType;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Trivial surge service. In prod: live computation per zone. */
public final class SurgeService {
    private final ConcurrentMap<String, BigDecimal> factors = new ConcurrentHashMap<>();
    private final BigDecimal defaultFactor = BigDecimal.ONE;

    public BigDecimal factor(Location pickup, RideType type) {
        String key = zone(pickup) + ":" + type.name();
        return factors.getOrDefault(key, defaultFactor);
    }

    public void set(Location pickup, RideType type, BigDecimal factor) {
        factors.put(zone(pickup) + ":" + type.name(), factor);
    }

    /** crude geohash7-ish key */
    private String zone(Location l) {
        return String.format("%.3f,%.3f", l.lat(), l.lng());
    }
}
