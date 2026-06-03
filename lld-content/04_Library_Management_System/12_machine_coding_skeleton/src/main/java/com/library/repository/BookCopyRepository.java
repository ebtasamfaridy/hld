package com.library.repository;

import com.library.domain.BookCopy;
import com.library.domain.CopyStatus;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class BookCopyRepository {
    private final ConcurrentMap<UUID, BookCopy> byId = new ConcurrentHashMap<>();

    public BookCopy save(BookCopy c) { byId.put(c.id(), c); return c; }
    public Optional<BookCopy> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

    public List<BookCopy> findByBook(UUID bookId) {
        return byId.values().stream().filter(c -> c.bookId().equals(bookId)).collect(Collectors.toList());
    }

    public Optional<BookCopy> findFirstAvailable(UUID bookId, UUID preferredBranch) {
        return byId.values().stream()
                .filter(c -> c.bookId().equals(bookId) && c.status() == CopyStatus.AVAILABLE)
                .sorted((a, b) -> {
                    boolean aPref = a.branchId().equals(preferredBranch);
                    boolean bPref = b.branchId().equals(preferredBranch);
                    return Boolean.compare(!aPref, !bPref);
                })
                .findFirst();
    }

    /** Atomic CAS. Returns true on success. */
    public synchronized boolean trySetStatusCAS(UUID copyId, CopyStatus from, CopyStatus to,
                                                long expectedVersion) {
        BookCopy c = byId.get(copyId);
        if (c == null) return false;
        if (c.status() != from) return false;
        if (c.version() != expectedVersion) return false;
        invokeSetStatus(c, to);
        return true;
    }

    private void invokeSetStatus(BookCopy c, CopyStatus s) {
        try {
            Method m = BookCopy.class.getDeclaredMethod("setStatus", CopyStatus.class);
            m.setAccessible(true);
            m.invoke(c, s);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
