package com.parking.domain;

public final class Compatibility {
    private Compatibility() {}

    public static boolean canPark(VehicleType v, SpotType s, boolean handicapPermit) {
        if (s == SpotType.HANDICAP && !handicapPermit) return false;
        return switch (v) {
            case BIKE   -> true;
            case CAR    -> s != SpotType.BIKE;
            case TRUCK  -> s == SpotType.LARGE;
            case EV_CAR -> s != SpotType.BIKE;
        };
    }

    /** EV cars prefer EV spots (for charging). Lower score = preferred. */
    public static int preferenceCost(VehicleType v, SpotType s) {
        if (v == VehicleType.EV_CAR && s == SpotType.EV) return -10;   // strong preference
        return 0;
    }
}
