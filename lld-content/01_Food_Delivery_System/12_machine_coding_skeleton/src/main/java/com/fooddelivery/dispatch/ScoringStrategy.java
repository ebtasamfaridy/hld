package com.fooddelivery.dispatch;

import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.Location;

public interface ScoringStrategy {
    /** Higher score = better candidate. */
    double score(Driver driver, Location pickup);
}
