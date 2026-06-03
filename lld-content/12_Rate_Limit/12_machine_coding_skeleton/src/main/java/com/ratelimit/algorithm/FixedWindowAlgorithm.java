package com.ratelimit.algorithm;

import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.RateKey;
import com.ratelimit.store.Store;

import java.time.Duration;

public final class FixedWindowAlgorithm implements Algorithm {

    public record State(long windowStartMs, long count) {}

    private final Store<State> store;
    public FixedWindowAlgorithm(Store<State> store) { this.store = store; }

    @Override
    public Decision check(RateKey key, LimitConfig cfg, long cost, long nowMs) {
        long windowMs = cfg.windowSeconds() * 1000L;
        final boolean[] allowed = { false };
        final State[] post = { null };

        store.compute(key.storeKey(), current -> {
            State s = current;
            if (s == null || nowMs - s.windowStartMs() >= windowMs) {
                s = new State((nowMs / windowMs) * windowMs, 0);
            }
            long newCount = s.count() + cost;
            if (newCount <= cfg.maxTokens()) {
                allowed[0] = true;
                post[0] = new State(s.windowStartMs(), newCount);
            } else {
                post[0] = s;
            }
            return post[0];
        });

        long resetMs = post[0].windowStartMs() + windowMs;
        if (allowed[0]) {
            return new Decision.Allow(cfg.maxTokens() - post[0].count(), resetMs);
        }
        return new Decision.Deny(Duration.ofMillis(resetMs - nowMs), cfg.maxTokens(), key.family());
    }
}
