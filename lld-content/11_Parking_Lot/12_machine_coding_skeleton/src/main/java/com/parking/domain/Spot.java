package com.parking.domain;

import java.util.concurrent.atomic.AtomicReference;

public final class Spot {
    private final SpotId id;
    private final int floor, row, col;
    private final SpotType type;
    private final AtomicReference<TicketId> currentTicket = new AtomicReference<>();
    private boolean outOfService = false;

    public Spot(SpotId id, int floor, int row, int col, SpotType type) {
        this.id = id; this.floor = floor; this.row = row; this.col = col; this.type = type;
    }

    public SpotId id()    { return id; }
    public int floor()    { return floor; }
    public int row()      { return row; }
    public int col()      { return col; }
    public SpotType type() { return type; }
    public boolean isOccupied() { return currentTicket.get() != null; }
    public boolean isOutOfService() { return outOfService; }
    public void setOutOfService(boolean v) { this.outOfService = v; }

    /** Atomic claim. Returns true if this caller won the spot. */
    public boolean tryClaim(TicketId t) {
        if (outOfService) return false;
        return currentTicket.compareAndSet(null, t);
    }

    /** Release only if the holder matches; idempotent for retries. */
    public boolean release(TicketId t) {
        return currentTicket.compareAndSet(t, null);
    }
}
