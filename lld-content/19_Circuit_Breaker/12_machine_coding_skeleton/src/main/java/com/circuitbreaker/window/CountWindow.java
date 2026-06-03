package com.circuitbreaker.window;

import java.util.concurrent.atomic.AtomicIntegerArray;

/** Count-based ring buffer of outcomes. Encoded as 0=success,1=failure,2=slow,-1=empty. */
public final class CountWindow implements SlidingWindow {

    private static final int EMPTY = -1, SUCCESS = 0, FAILURE = 1, SLOW = 2;

    private final AtomicIntegerArray slots;
    private final java.util.concurrent.atomic.AtomicInteger nextIndex = new java.util.concurrent.atomic.AtomicInteger(0);
    private final int size;

    public CountWindow(int size) {
        this.size = size;
        this.slots = new AtomicIntegerArray(size);
        for (int i = 0; i < size; i++) slots.set(i, EMPTY);
    }

    @Override public void recordSuccess() { record(SUCCESS); }
    @Override public void recordFailure() { record(FAILURE); }
    @Override public void recordSlow()    { record(SLOW); }

    private void record(int outcome) {
        int i = Math.floorMod(nextIndex.getAndIncrement(), size);
        slots.set(i, outcome);
    }

    @Override
    public synchronized void reset() {
        for (int i = 0; i < size; i++) slots.set(i, EMPTY);
        nextIndex.set(0);
    }

    @Override
    public Snapshot snapshot() {
        int total = 0, fail = 0, slow = 0;
        for (int i = 0; i < size; i++) {
            int v = slots.get(i);
            if (v == EMPTY) continue;
            total++;
            if (v == FAILURE) fail++;
            if (v == SLOW)    slow++;
        }
        return new Snapshot(total, fail, slow);
    }
}
