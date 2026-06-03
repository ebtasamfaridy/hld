package com.ratelimit;

import com.ratelimit.algorithm.Algorithm;
import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.RateKey;
import com.ratelimit.domain.Request;
import com.ratelimit.middleware.KeyExtractor;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class RateLimiter {

    private final KeyExtractor extractor;
    private final Map<String, LimitConfig> configByFamily;
    private final Map<String, Algorithm> algorithmByFamily;
    private final Clock clock;

    public RateLimiter(KeyExtractor extractor,
                       Map<String, LimitConfig> configByFamily,
                       Map<String, Algorithm> algorithmByFamily,
                       Clock clock) {
        this.extractor = extractor;
        this.configByFamily = configByFamily;
        this.algorithmByFamily = algorithmByFamily;
        this.clock = clock;
    }

    public Decision check(Request req)             { return check(req, 1); }
    public Decision check(Request req, long cost) {
        long now = clock.millis();
        List<RateKey> keys = extractor.keysFor(req);
        for (RateKey k : keys) {
            LimitConfig cfg = configByFamily.get(k.family());
            Algorithm algo = algorithmByFamily.get(k.family());
            if (cfg == null || algo == null) continue;
            Decision d = algo.check(k, cfg, cost, now);
            if (d instanceof Decision.Deny deny) return deny;
        }
        // Use the last successful Allow as the indicator (or a synthetic one).
        return new Decision.Allow(-1, now);
    }
}
