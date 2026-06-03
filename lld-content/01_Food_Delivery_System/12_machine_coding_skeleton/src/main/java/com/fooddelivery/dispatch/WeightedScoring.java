package com.fooddelivery.dispatch;

import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.Location;

public final class WeightedScoring implements ScoringStrategy {
    private final double distanceWeight;
    private final double ratingWeight;

    public WeightedScoring(double distanceWeight, double ratingWeight) {
        this.distanceWeight = distanceWeight;
        this.ratingWeight = ratingWeight;
    }

    @Override public double score(Driver d, Location pickup) {
        double dist = d.lastLocation().distanceKm(pickup);
        return -dist * distanceWeight + d.rating() * ratingWeight;
    }
}
