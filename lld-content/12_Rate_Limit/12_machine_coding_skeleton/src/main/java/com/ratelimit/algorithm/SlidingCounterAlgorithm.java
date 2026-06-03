package com.ratelimit.algorithm;

import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.RateKey;
import com.ratelimit.store.Store;

import java.time.Duration;

/**
 * Sliding Window Counter — O(1) state, ~5% accuracy.
 * State: current window count + previous window count + current window start.
 */
public final class SlidingCounterAlgorithm implements Algorithm {

    public record State(long currentWindowStartMs, long currentCount, long previousCount) {}

    private final Store<State> store;
    public SlidingCounterAlgorithm(Store<State> store) { this.store = store; }

    @Override
    public Decision check(RateKey key, LimitConfig cfg, long cost, long nowMs) {
        long windowMs = cfg.windowSeconds() * 1000L;
        final boolean[] allowed = { false };
        final long[] weighted = { 0 };

        store.compute(key.storeKey(), current -> {
            State s = current == null
                    ? new State((nowMs / windowMs) * windowMs, 0, 0)
                    : current;
            long currentWindow = (nowMs / windowMs) * windowMs;
            if (s.currentWindowStartMs() != currentWindow) {
                long shift = (currentWindow - s.currentWindowStartMs()) / windowMs;
                long prev = (shift == 1) ? s.currentCount() : 0;
                s = new State(currentWindow, 0, prev);
            }
            double elapsedFraction = (nowMs - s.currentWindowStartMs()) / (double) windowMs;
            long w = Math.round(s.currentCount() + s.previousCount() * (1 - elapsedFraction));
            if (w + cost <= cfg.maxTokens()) {
                allowed[0] = true;
                weighted[0] = w + cost;
                return new State(s.currentWindowStartMs(), s.currentCount() + cost, s.previousCount());
            }
            weighted[0] = w;
            return s;
        });

        long resetMs = nowMs + windowMs;
        if (allowed[0]) {
            return new Decision.Allow(cfg.maxTokens() - weighted[0], resetMs);
        }
        return new Decision.Deny(Duration.ofMillis(windowMs / 4), cfg.maxTokens(), key.family());
    }
}
