package com.fooddelivery.dispatch;

import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.Location;

public final class NearestFirstScoring implements ScoringStrategy {
    @Override public double score(Driver d, Location pickup) {
        return -d.lastLocation().distanceKm(pickup);   // closer is higher score
    }
}
