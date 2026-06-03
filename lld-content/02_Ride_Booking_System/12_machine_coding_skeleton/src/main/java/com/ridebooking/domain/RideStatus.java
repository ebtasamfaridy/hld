package com.ridebooking.domain;

import java.util.Map;
import java.util.Set;

public enum RideStatus {
    REQUESTED, MATCHED, ARRIVING, ARRIVED, IN_TRIP, COMPLETED, CANCELLED, NO_SHOW;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = Map.of(
        REQUESTED, Set.of(MATCHED, CANCELLED),
        MATCHED,   Set.of(ARRIVING, CANCELLED),
        ARRIVING,  Set.of(ARRIVED, CANCELLED),
        ARRIVED,   Set.of(IN_TRIP, NO_SHOW, CANCELLED),
        IN_TRIP,   Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(),
        CANCELLED, Set.of(),
        NO_SHOW,   Set.of()
    );

    public boolean canTransitionTo(RideStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public static void requireTransition(RideStatus from, RideStatus to) {
        if (!from.canTransitionTo(to))
            throw new IllegalStateException("Illegal: " + from + " -> " + to);
    }
}
