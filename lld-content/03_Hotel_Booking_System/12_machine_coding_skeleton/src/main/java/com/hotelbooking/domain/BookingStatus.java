package com.hotelbooking.domain;

import java.util.Map;
import java.util.Set;

public enum BookingStatus {
    PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
        PENDING,     Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,   Set.of(CHECKED_IN, CANCELLED, NO_SHOW),
        CHECKED_IN,  Set.of(CHECKED_OUT),
        CHECKED_OUT, Set.of(),
        CANCELLED,   Set.of(),
        NO_SHOW,     Set.of()
    );

    public boolean canTransitionTo(BookingStatus n) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(n);
    }
    public static void requireTransition(BookingStatus f, BookingStatus t) {
        if (!f.canTransitionTo(t)) throw new IllegalStateException("Illegal: " + f + " -> " + t);
    }
}
