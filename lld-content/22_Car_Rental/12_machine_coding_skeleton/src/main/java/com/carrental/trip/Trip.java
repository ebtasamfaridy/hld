package com.carrental.trip;

import com.carrental.domain.Enums.TripStatus;
import com.carrental.domain.GeoPoint;
import com.carrental.domain.Ids;
import com.carrental.domain.Money;

import java.time.Instant;
import java.util.UUID;

public final class Trip {
    private final UUID id;
    private final UUID reservationId;
    private final Instant pickedUpAt;
    private final int odoStartKm;
    private final int fuelStartPct;
    private final GeoPoint pickupGps;

    private volatile Instant returnedAt;
    private volatile Integer odoEndKm;
    private volatile Integer fuelEndPct;
    private volatile GeoPoint returnGps;
    private volatile Money finalFare;
    private volatile boolean needsCleaning;
    private volatile TripStatus status = TripStatus.PICKED_UP;

    public Trip(UUID reservationId, Instant pickedUpAt, int odoStartKm, int fuelStartPct, GeoPoint pickupGps) {
        this.id = Ids.newId();
        this.reservationId = reservationId;
        this.pickedUpAt = pickedUpAt;
        this.odoStartKm = odoStartKm;
        this.fuelStartPct = fuelStartPct;
        this.pickupGps = pickupGps;
    }

    public UUID id() { return id; }
    public UUID reservationId() { return reservationId; }
    public Instant pickedUpAt() { return pickedUpAt; }
    public int odoStartKm() { return odoStartKm; }
    public int fuelStartPct() { return fuelStartPct; }
    public GeoPoint pickupGps() { return pickupGps; }

    public Instant returnedAt() { return returnedAt; }
    public Integer odoEndKm() { return odoEndKm; }
    public Integer fuelEndPct() { return fuelEndPct; }
    public GeoPoint returnGps() { return returnGps; }
    public Money finalFare() { return finalFare; }
    public boolean needsCleaning() { return needsCleaning; }
    public TripStatus status() { return status; }

    public void markReturned(Instant when, int odoEnd, int fuelEnd, GeoPoint gps, boolean cleaningNeeded) {
        this.returnedAt = when;
        this.odoEndKm = odoEnd;
        this.fuelEndPct = fuelEnd;
        this.returnGps = gps;
        this.needsCleaning = cleaningNeeded;
        this.status = TripStatus.RETURNED;
    }

    public void setFinalFare(Money m) { this.finalFare = m; }

    @Override public String toString() {
        return "Trip{" + Ids.shortHex(id) + " status=" + status
                + (odoEndKm != null ? " km=" + (odoEndKm - odoStartKm) : "")
                + "}";
    }
}
