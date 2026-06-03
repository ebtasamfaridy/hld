package com.circuitbreaker.window;

public interface SlidingWindow {
    void recordSuccess();
    void recordFailure();
    void recordSlow();
    void reset();

    Snapshot snapshot();

    record Snapshot(int totalCalls, int failures, int slowCalls) {
        public double failureRate() {
            return totalCalls == 0 ? 0 : (double) failures / totalCalls;
        }
        public double slowCallRate() {
            return totalCalls == 0 ? 0 : (double) slowCalls / totalCalls;
        }
    }
}
