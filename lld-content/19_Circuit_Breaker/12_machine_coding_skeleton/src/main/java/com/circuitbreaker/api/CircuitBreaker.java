package com.circuitbreaker.api;

import java.util.function.Supplier;

public interface CircuitBreaker {
    String name();
    State state();
    CircuitBreakerConfig config();

    AcquireResult acquire();
    void onSuccess(long durationNanos);
    void onError(long durationNanos, Throwable t);
    void onRejected();

    void transitionToOpen();
    void transitionToClosed();
    void transitionToForcedOpen();
    void transitionToDisabled();
    void reset();

    void addListener(EventListener l);

    default <T> T execute(Supplier<T> supplier) {
        if (acquire() == AcquireResult.REJECTED) {
            onRejected();
            throw new CallNotPermittedException(name());
        }
        long start = System.nanoTime();
        try {
            T value = supplier.get();
            onSuccess(System.nanoTime() - start);
            return value;
        } catch (Throwable t) {
            onError(System.nanoTime() - start, t);
            if (t instanceof RuntimeException re) throw re;
            if (t instanceof Error e) throw e;
            throw new RuntimeException(t);
        }
    }
}
