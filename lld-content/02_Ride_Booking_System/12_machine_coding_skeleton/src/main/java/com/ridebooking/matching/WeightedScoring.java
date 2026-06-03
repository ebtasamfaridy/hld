package com.ridebooking.matching;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.Location;

public final class WeightedScoring implements ScoringStrategy {
    private final double distanceWeight;
    private final double ratingWeight;

    public WeightedScoring(double distanceWeight, double ratingWeight) {
        this.distanceWeight = distanceWeight;
        this.ratingWeight = ratingWeight;
    }

    @Override public double score(Driver d, Location pickup) {
        return -d.lastLocation().distanceKm(pickup) * distanceWeight
             + d.rating() * ratingWeight;
    }
}
