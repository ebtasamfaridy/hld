package com.circuitbreaker.policy;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class Bulkhead {

    public static final class BulkheadFullException extends RuntimeException {
        public BulkheadFullException(String name) { super("Bulkhead '" + name + "' full"); }
    }

    private final String name;
    private final Semaphore sem;
    private final long acquireTimeoutMs;

    public Bulkhead(String name, int maxConcurrent, long acquireTimeoutMs) {
        this.name = name;
        this.sem = new Semaphore(maxConcurrent);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public <T> T execute(Supplier<T> supplier) {
        boolean acquired;
        try {
            acquired = sem.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (!acquired) throw new BulkheadFullException(name);
        try { return supplier.get(); }
        finally { sem.release(); }
    }
}
