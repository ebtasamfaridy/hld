package com.library.domain;

import java.time.Instant;
import java.util.UUID;

public final class Reservation {
    public enum Status { QUEUED, READY, EXPIRED, FULFILLED, CANCELLED }

    private final UUID id;
    private final UUID memberId;
    private final UUID bookId;
    private final Instant createdAt;
    private Status status = Status.QUEUED;
    private int queuePosition;
    private UUID heldCopyId;
    private Instant readyAt;
    private Instant expiresAt;

    public Reservation(UUID memberId, UUID bookId, int queuePosition) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.bookId = bookId;
        this.queuePosition = queuePosition;
        this.createdAt = Instant.now();
    }

    public void promote(UUID copyId, int holdHours) {
        if (status != Status.QUEUED) throw new IllegalStateException();
        status = Status.READY;
        heldCopyId = copyId;
        readyAt = Instant.now();
        expiresAt = readyAt.plusSeconds(holdHours * 3600L);
    }
    public void fulfill() {
        if (status != Status.READY) throw new IllegalStateException();
        status = Status.FULFILLED;
    }
    public void expire() {
        if (status != Status.READY) throw new IllegalStateException();
        status = Status.EXPIRED;
    }
    public void cancel() {
        if (status != Status.QUEUED && status != Status.READY) throw new IllegalStateException();
        status = Status.CANCELLED;
    }

    public UUID id() { return id; }
    public UUID memberId() { return memberId; }
    public UUID bookId() { return bookId; }
    public Status status() { return status; }
    public int queuePosition() { return queuePosition; }
    public UUID heldCopyId() { return heldCopyId; }
    public Instant readyAt() { return readyAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }

    @Override public String toString() {
        return "Reservation{" + status + " pos=" + queuePosition
             + (heldCopyId != null ? " held=" + heldCopyId.toString().substring(0,8) : "") + "}";
    }
}
