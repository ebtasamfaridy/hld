package com.library.domain;

import java.util.UUID;

public final class BookCopy {
    private final UUID id;
    private final UUID bookId;
    private UUID branchId;
    private CopyStatus status;
    private long version;

    public BookCopy(UUID bookId, UUID branchId) {
        this.id = UUID.randomUUID();
        this.bookId = bookId;
        this.branchId = branchId;
        this.status = CopyStatus.AVAILABLE;
    }
    public UUID id() { return id; }
    public UUID bookId() { return bookId; }
    public UUID branchId() { return branchId; }
    public CopyStatus status() { return status; }
    public long version() { return version; }

    /** Package-private mutator used by repository's CAS. */
    void setStatus(CopyStatus s) { this.status = s; this.version++; }
    void setBranch(UUID b) { this.branchId = b; this.version++; }

    @Override public String toString() {
        return "Copy{" + id.toString().substring(0,8) + " " + status + " v=" + version + "}";
    }
}
