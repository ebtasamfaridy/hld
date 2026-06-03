package com.fooddelivery.domain;

/** Immutable lat/lng location with great-circle distance. */
public final class Location {
    private static final double EARTH_RADIUS_KM = 6371.0;
    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        if (lat < -90 || lat > 90)   throw new IllegalArgumentException("lat out of range");
        if (lng < -180 || lng > 180) throw new IllegalArgumentException("lng out of range");
        this.lat = lat;
        this.lng = lng;
    }

    public double lat() { return lat; }
    public double lng() { return lng; }

    public double distanceKm(Location other) {
        double dLat = Math.toRadians(other.lat - lat);
        double dLng = Math.toRadians(other.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    @Override public String toString() {
        return "(" + lat + "," + lng + ")";
    }
}
