package com.ridebooking.domain;

public final class Location {
    private static final double EARTH_R = 6371.0;
    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double lat() { return lat; }
    public double lng() { return lng; }

    public double distanceKm(Location o) {
        double dLat = Math.toRadians(o.lat - lat);
        double dLng = Math.toRadians(o.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(o.lat))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override public String toString() { return "(" + lat + "," + lng + ")"; }
}
