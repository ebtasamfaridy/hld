package com.ratelimit.algorithm;

import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.RateKey;
import com.ratelimit.store.Store;

import java.time.Duration;

/**
 * Token Bucket — production default.
 * State: { tokens, lastRefillMs }
 */
public final class TokenBucketAlgorithm implements Algorithm {

    public record State(double tokens, long lastRefillMs) {}

    private final Store<State> store;

    public TokenBucketAlgorithm(Store<State> store) { this.store = store; }

    @Override
    public Decision check(RateKey key, LimitConfig cfg, long cost, long nowMs) {
        var ratePerMs = cfg.refillPerSec() / 1000.0;
        var capacity  = (double) cfg.maxTokens();

        // The atomic update; returns the post-update state. We also need the
        // "allowed?" signal — encode it via the sign of tokens-before vs cost.
        final boolean[] allowed = { false };
        final double[] postTokens = { 0 };

        store.compute(key.storeKey(), current -> {
            State s = (current == null)
                    ? new State(capacity, nowMs)
                    : current;
            double elapsed = Math.max(0, nowMs - s.lastRefillMs());
            double tokens = Math.min(capacity, s.tokens() + elapsed * ratePerMs);
            if (tokens >= cost) {
                tokens -= cost;
                allowed[0] = true;
            }
            postTokens[0] = tokens;
            return new State(tokens, nowMs);
        });

        if (allowed[0]) {
            long remaining = (long) Math.floor(postTokens[0]);
            long resetMs = nowMs + (long) Math.ceil((capacity - postTokens[0]) / ratePerMs);
            return new Decision.Allow(remaining, resetMs);
        }
        long retryMs = (long) Math.ceil((cost - postTokens[0]) / ratePerMs);
        return new Decision.Deny(Duration.ofMillis(retryMs), cfg.maxTokens(), key.family());
    }
}
