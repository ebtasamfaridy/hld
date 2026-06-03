package com.carrental.inventory;

import com.carrental.domain.HourBucket;

import java.util.Objects;
import java.util.UUID;

public final class TimeSlot {
    private final UUID vehicleId;
    private final HourBucket bucket;
    private final UUID reservationId;

    public TimeSlot(UUID vehicleId, HourBucket bucket, UUID reservationId) {
        this.vehicleId = vehicleId;
        this.bucket = bucket;
        this.reservationId = reservationId;
    }
    public UUID vehicleId()     { return vehicleId; }
    public HourBucket bucket()  { return bucket; }
    public UUID reservationId() { return reservationId; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof TimeSlot s)) return false;
        return Objects.equals(vehicleId, s.vehicleId) && Objects.equals(bucket, s.bucket);
    }
    @Override public int hashCode() { return Objects.hash(vehicleId, bucket); }
}
