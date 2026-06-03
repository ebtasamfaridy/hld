package com.circuitbreaker.core;

import com.circuitbreaker.api.AcquireResult;
import com.circuitbreaker.api.CircuitBreaker;
import com.circuitbreaker.api.CircuitBreakerConfig;
import com.circuitbreaker.api.EventListener;
import com.circuitbreaker.api.State;
import com.circuitbreaker.window.CountWindow;
import com.circuitbreaker.window.SlidingWindow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class StandardCircuitBreaker implements CircuitBreaker {

    private final CircuitBreakerConfig config;
    private final SlidingWindow window;
    private final AtomicReference<State> stateRef = new AtomicReference<>(State.CLOSED);
    private volatile long openUntilNanos;
    private final Semaphore halfOpenPermits;
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger();
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    public StandardCircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
        this.window = new CountWindow(config.windowSize);
        this.halfOpenPermits = new Semaphore(config.permittedCallsInHalfOpen);
        // start with permits drained except in CLOSED→HALF_OPEN entry
        halfOpenPermits.drainPermits();
    }

    @Override public String name()                  { return config.name; }
    @Override public State state()                   { return stateRef.get(); }
    @Override public CircuitBreakerConfig config()   { return config; }
    @Override public void addListener(EventListener l) { listeners.add(l); }

    @Override
    public AcquireResult acquire() {
        State s = stateRef.get();
        return switch (s) {
            case CLOSED, FORCED_CLOSED, DISABLED -> AcquireResult.ALLOWED;
            case FORCED_OPEN -> AcquireResult.REJECTED;
            case OPEN -> {
                if (System.nanoTime() < openUntilNanos) {
                    yield AcquireResult.REJECTED;
                }
                if (stateRef.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenPermits.release(config.permittedCallsInHalfOpen);
                    halfOpenSuccesses.set(0);
                    publishStateChange(State.OPEN, State.HALF_OPEN, "wait elapsed");
                }
                yield halfOpenPermits.tryAcquire() ? AcquireResult.ALLOWED : AcquireResult.REJECTED;
            }
            case HALF_OPEN -> halfOpenPermits.tryAcquire() ? AcquireResult.ALLOWED : AcquireResult.REJECTED;
        };
    }

    @Override
    public void onSuccess(long durationNanos) {
        boolean slow = durationNanos >= config.slowCallThreshold.toNanos();
        if (slow) window.recordSlow(); else window.recordSuccess();

        for (EventListener l : listeners) {
            try { if (slow) l.onCallSlow(config.name, durationNanos); else l.onCallSuccess(config.name, durationNanos); }
            catch (Throwable ignored) {}
        }

        State s = stateRef.get();
        if (s == State.HALF_OPEN) {
            int n = halfOpenSuccesses.incrementAndGet();
            if (n >= config.permittedCallsInHalfOpen) {
                if (stateRef.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    window.reset();
                    halfOpenPermits.drainPermits();
                    publishStateChange(State.HALF_OPEN, State.CLOSED, "probes succeeded");
                }
            }
        } else if (s == State.CLOSED) {
            evaluateAndMaybeTrip();
        }
    }

    @Override
    public void onError(long durationNanos, Throwable t) {
        if (!config.classifier.isFailure(t)) {
            // not a real failure for breaker purposes
            window.recordSuccess();
            return;
        }
        window.recordFailure();
        for (EventListener l : listeners) {
            try { l.onCallFailure(config.name, durationNanos, t); }
            catch (Throwable ignored) {}
        }

        State s = stateRef.get();
        if (s == State.HALF_OPEN) {
            if (stateRef.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openUntilNanos = System.nanoTime() + config.waitDurationInOpen.toNanos();
                halfOpenPermits.drainPermits();
                publishStateChange(State.HALF_OPEN, State.OPEN, "probe failed");
            }
        } else if (s == State.CLOSED) {
            evaluateAndMaybeTrip();
        }
    }

    @Override
    public void onRejected() {
        for (EventListener l : listeners) {
            try { l.onCallRejected(config.name); }
            catch (Throwable ignored) {}
        }
    }

    private void evaluateAndMaybeTrip() {
        SlidingWindow.Snapshot snap = window.snapshot();
        if (snap.totalCalls() < config.minimumCalls) return;
        boolean trip = snap.failureRate() >= config.failureRateThreshold
                || snap.slowCallRate() >= config.slowCallRateThreshold;
        if (!trip) return;
        if (stateRef.compareAndSet(State.CLOSED, State.OPEN)) {
            openUntilNanos = System.nanoTime() + config.waitDurationInOpen.toNanos();
            publishStateChange(State.CLOSED, State.OPEN,
                    "failureRate=" + String.format("%.2f", snap.failureRate())
                  + " slowRate=" + String.format("%.2f", snap.slowCallRate()));
        }
    }

    private void publishStateChange(State from, State to, String reason) {
        for (EventListener l : listeners) {
            try { l.onStateChange(config.name, from, to, reason); }
            catch (Throwable ignored) {}
        }
    }

    @Override public void transitionToOpen()        { transitionTo(State.OPEN, "manual"); openUntilNanos = System.nanoTime() + config.waitDurationInOpen.toNanos(); }
    @Override public void transitionToClosed()      { transitionTo(State.CLOSED, "manual"); window.reset(); }
    @Override public void transitionToForcedOpen()  { transitionTo(State.FORCED_OPEN, "manual"); }
    @Override public void transitionToDisabled()    { transitionTo(State.DISABLED, "manual"); }
    @Override public void reset() {
        State old = stateRef.getAndSet(State.CLOSED);
        window.reset();
        halfOpenPermits.drainPermits();
        if (old != State.CLOSED) publishStateChange(old, State.CLOSED, "reset");
    }

    private void transitionTo(State to, String reason) {
        State from = stateRef.getAndSet(to);
        if (from != to) publishStateChange(from, to, reason);
    }
}
