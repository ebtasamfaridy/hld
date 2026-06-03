package com.fooddelivery.domain;

import java.time.Instant;
import java.util.UUID;

public final class DeliveryAssignment {
    private final UUID id;
    private final UUID orderId;
    private final UUID driverId;
    private final Instant offeredAt;
    private final Instant expiresAt;

    private AssignmentStatus status;
    private Instant respondedAt;
    private Instant pickedUpAt;
    private Instant deliveredAt;
    private long version;

    public DeliveryAssignment(UUID orderId, UUID driverId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.driverId = driverId;
        this.offeredAt = Instant.now();
        this.expiresAt = expiresAt;
        this.status = AssignmentStatus.OFFERED;
        this.version = 0;
    }

    public void accept() {
        require(AssignmentStatus.OFFERED);
        status = AssignmentStatus.ACCEPTED;
        respondedAt = Instant.now();
        version++;
    }

    public void reject() {
        require(AssignmentStatus.OFFERED);
        status = AssignmentStatus.REJECTED;
        respondedAt = Instant.now();
        version++;
    }

    public void expire() {
        require(AssignmentStatus.OFFERED);
        status = AssignmentStatus.EXPIRED;
        version++;
    }

    public void pickedUp() {
        require(AssignmentStatus.ACCEPTED);
        status = AssignmentStatus.PICKED_UP;
        pickedUpAt = Instant.now();
        version++;
    }

    public void delivered() {
        require(AssignmentStatus.PICKED_UP);
        status = AssignmentStatus.DELIVERED;
        deliveredAt = Instant.now();
        version++;
    }

    private void require(AssignmentStatus expected) {
        if (status != expected)
            throw new IllegalStateException("assignment " + id + " in " + status + " not " + expected);
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public UUID driverId() { return driverId; }
    public AssignmentStatus status() { return status; }
    public Instant offeredAt() { return offeredAt; }
    public Instant expiresAt() { return expiresAt; }
    public long version() { return version; }
}
