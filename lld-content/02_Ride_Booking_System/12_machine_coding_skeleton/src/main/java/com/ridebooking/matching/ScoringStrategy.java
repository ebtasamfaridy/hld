package com.ridebooking.matching;

import com.ridebooking.domain.Driver;
import com.ridebooking.domain.Location;

public interface ScoringStrategy {
    /** Higher = better. */
    double score(Driver driver, Location pickup);
}
