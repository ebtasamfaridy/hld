package com.ridebooking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Ride {
    private final UUID id;
    private final UUID riderId;
    private final RideType type;
    private final Location pickup;
    private final Location drop;
    private final FareEstimate estimate;
    private final BigDecimal surgeFactorLocked;
    private final String idempotencyKey;
    private final Instant requestedAt;
    private final String otp;          // shown to rider, validated at start

    private RideStatus status;
    private UUID driverId;
    private FareFinal finalFare;
    private double tripKm;
    private int tripMinutes;
    private Money cancellationFee;
    private long version;

    private Ride(Builder b) {
        this.id = b.id;
        this.riderId = b.riderId;
        this.type = b.type;
        this.pickup = b.pickup;
        this.drop = b.drop;
        this.estimate = b.estimate;
        this.surgeFactorLocked = b.surgeFactorLocked;
        this.idempotencyKey = b.idempotencyKey;
        this.status = RideStatus.REQUESTED;
        this.requestedAt = Instant.now();
        this.otp = b.otp != null ? b.otp : String.format("%04d", (int)(Math.random() * 10000));
    }

    public static Builder builder() { return new Builder(); }

    public void match(UUID driverId) {
        transition(RideStatus.MATCHED);
        this.driverId = driverId;
    }
    public void arriving() { transition(RideStatus.ARRIVING); }
    public void arrived()  { transition(RideStatus.ARRIVED); }
    public void start(String otp) {
        if (!this.otp.equals(otp)) throw new IllegalArgumentException("OTP mismatch");
        transition(RideStatus.IN_TRIP);
    }
    public void end(double km, int minutes, FareFinal finalFare) {
        transition(RideStatus.COMPLETED);
        this.tripKm = km;
        this.tripMinutes = minutes;
        this.finalFare = finalFare;
    }
    public void cancel(Money fee) {
        if (status == RideStatus.COMPLETED)
            throw new IllegalStateException("already completed");
        transition(RideStatus.CANCELLED);
        this.cancellationFee = fee;
    }
    public void noShow(Money fee) {
        transition(RideStatus.NO_SHOW);
        this.cancellationFee = fee;
    }

    private void transition(RideStatus next) {
        RideStatus.requireTransition(status, next);
        status = next;
        version++;
    }

    public UUID id() { return id; }
    public UUID riderId() { return riderId; }
    public UUID driverId() { return driverId; }
    public RideType type() { return type; }
    public RideStatus status() { return status; }
    public Location pickup() { return pickup; }
    public Location drop() { return drop; }
    public FareEstimate estimate() { return estimate; }
    public BigDecimal surgeFactorLocked() { return surgeFactorLocked; }
    public String idempotencyKey() { return idempotencyKey; }
    public String otp() { return otp; }
    public FareFinal finalFare() { return finalFare; }
    public Money cancellationFee() { return cancellationFee; }
    public long version() { return version; }
    public Instant requestedAt() { return requestedAt; }

    @Override public String toString() {
        return "Ride{id=" + id + ", " + status + ", driver=" + driverId
             + ", v=" + version + (finalFare != null ? ", " + finalFare : "") + "}";
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private UUID riderId;
        private RideType type;
        private Location pickup;
        private Location drop;
        private FareEstimate estimate;
        private BigDecimal surgeFactorLocked = BigDecimal.ONE;
        private String idempotencyKey;
        private String otp;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder rider(UUID id) { this.riderId = id; return this; }
        public Builder type(RideType t) { this.type = t; return this; }
        public Builder pickup(Location l) { this.pickup = l; return this; }
        public Builder drop(Location l) { this.drop = l; return this; }
        public Builder estimate(FareEstimate e) { this.estimate = e; return this; }
        public Builder surge(BigDecimal s) { this.surgeFactorLocked = s; return this; }
        public Builder idempotencyKey(String k) { this.idempotencyKey = k; return this; }
        public Builder otp(String o) { this.otp = o; return this; }

        public Ride build() {
            if (riderId == null || type == null || pickup == null || drop == null)
                throw new IllegalStateException("missing required fields");
            return new Ride(this);
        }
    }
}
