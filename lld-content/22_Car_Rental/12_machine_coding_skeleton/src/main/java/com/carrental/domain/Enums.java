package com.carrental.domain;

public final class Enums {
    private Enums() {}

    public enum VehicleStatus { ACTIVE, MAINTENANCE, OUT_OF_SERVICE, RETIRED }

    public enum ReservationStatus {
        HELD, CONFIRMED, ACTIVE, COMPLETED, CANCELLED, NO_SHOW, EXPIRED
    }

    public enum TripStatus { PICKED_UP, IN_USE, RETURNED, DISPUTED }

    public enum PaymentStatus {
        CREATED, AUTHORIZED, CAPTURED, FAILED, VOIDED, PARTIALLY_REFUNDED, REFUNDED
    }
}
