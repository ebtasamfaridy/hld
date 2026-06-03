package com.carrental.domain;

public record GeoPoint(double lat, double lng) {

    /** Haversine distance in metres (good enough for fence checks). */
    public double distanceMetres(GeoPoint other) {
        final double R = 6_371_000.0;
        double phi1 = Math.toRadians(this.lat);
        double phi2 = Math.toRadians(other.lat);
        double dPhi = Math.toRadians(other.lat - this.lat);
        double dLng = Math.toRadians(other.lng - this.lng);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                 + Math.cos(phi1) * Math.cos(phi2)
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
