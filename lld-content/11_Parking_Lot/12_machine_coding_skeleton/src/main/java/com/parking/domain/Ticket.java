package com.parking.domain;

import java.time.Instant;

public final class Ticket {
    public enum Status { ACTIVE, PAID, CLOSED }

    private final TicketId id;
    private final String plate;
    private final VehicleType vehicleType;
    private final SpotId spot;
    private final Instant enteredAt;
    private Instant exitedAt;
    private Money feeCharged;
    private String paymentRef;
    private Status status = Status.ACTIVE;

    public Ticket(TicketId id, String plate, VehicleType type, SpotId spot, Instant enteredAt) {
        this.id = id; this.plate = plate; this.vehicleType = type; this.spot = spot; this.enteredAt = enteredAt;
    }

    public TicketId id()             { return id; }
    public String plate()            { return plate; }
    public VehicleType vehicleType() { return vehicleType; }
    public SpotId spot()             { return spot; }
    public Instant enteredAt()       { return enteredAt; }
    public Instant exitedAt()        { return exitedAt; }
    public Money feeCharged()        { return feeCharged; }
    public Status status()           { return status; }

    public void close(Instant exit, Money fee, String paymentRef) {
        this.exitedAt = exit;
        this.feeCharged = fee;
        this.paymentRef = paymentRef;
        this.status = Status.CLOSED;
    }
}
