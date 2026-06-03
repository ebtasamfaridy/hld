package com.ratelimit;

import com.ratelimit.algorithm.Algorithm;
import com.ratelimit.algorithm.SlidingCounterAlgorithm;
import com.ratelimit.algorithm.TokenBucketAlgorithm;
import com.ratelimit.domain.Decision;
import com.ratelimit.domain.LimitConfig;
import com.ratelimit.domain.Request;
import com.ratelimit.middleware.CompositeKeyExtractor;
import com.ratelimit.store.InMemoryStore;

import java.time.*;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        // Build per-family algorithms with their own stores.
        var ipStore   = new InMemoryStore<TokenBucketAlgorithm.State>();
        var userStore = new InMemoryStore<TokenBucketAlgorithm.State>();
        var routeStore = new InMemoryStore<SlidingCounterAlgorithm.State>();

        var ipAlgo    = new TokenBucketAlgorithm(ipStore);
        var userAlgo  = new TokenBucketAlgorithm(userStore);
        var routeAlgo = new SlidingCounterAlgorithm(routeStore);

        Map<String, Algorithm> algos = Map.of(
                "ip",    ipAlgo,
                "user",  userAlgo,
                "route", routeAlgo);

        Map<String, LimitConfig> configs = Map.of(
                "ip",    LimitConfig.tokenBucket(20, 10),     // 20 burst, 10 RPS sustained
                "user",  LimitConfig.tokenBucket(5, 1),       // 5 burst, 1 RPS sustained
                "route", LimitConfig.fixedWindow(50, 60));    // 50/min per route

        // Mutable virtual clock so the demo isn't time-flaky.
        final long[] now = { System.currentTimeMillis() };
        Clock clock = new Clock() {
            @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
            @Override public ZoneId getZone()  { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };

        var limiter = new RateLimiter(
                new CompositeKeyExtractor(true, true, true),
                configs, algos, clock);

        section("Burst 6 requests for user u-42");
        var req = new Request("1.2.3.4", "u-42", "GET /search", "k-1");
        for (int i = 0; i < 6; i++) {
            Decision d = limiter.check(req);
            System.out.printf("  %d: %s%n", i + 1, d);
        }

        section("Sleep 3s (virtual); 3 more should pass");
        now[0] += 3_000;
        for (int i = 0; i < 3; i++) {
            Decision d = limiter.check(req);
            System.out.printf("  %d: %s%n", i + 1, d);
        }

        section("Different user — independent budget");
        var other = new Request("9.9.9.9", "u-99", "GET /search", "k-9");
        for (int i = 0; i < 5; i++) {
            Decision d = limiter.check(other);
            System.out.printf("  %d: %s%n", i + 1, d);
        }
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
