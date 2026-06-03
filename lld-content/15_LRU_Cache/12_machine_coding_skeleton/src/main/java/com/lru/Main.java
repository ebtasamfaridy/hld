package com.lru;

import com.lru.cache.Cache;
import com.lru.cache.CacheBuilder;
import com.lru.store.CacheLoader;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {
    public static void main(String[] args) {
        final Instant[] now = { Instant.parse("2026-04-29T18:00:00Z") };
        Clock clock = new Clock() {
            @Override public Instant instant()        { return now[0]; }
            @Override public ZoneId getZone()         { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };

        Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
                .capacity(3)
                .ttl(Duration.ofMinutes(10))
                .clock(clock)
                .evictionListener((k, v, cause) ->
                        System.out.printf("    [evict] %s=%s cause=%s%n", k, v, cause))
                .build();

        section("put a, b, c (cache full)");
        cache.put("a", "A"); cache.put("b", "B"); cache.put("c", "C");
        System.out.println("  size=" + cache.size());

        section("get a → moves to MRU");
        System.out.println("  a → " + cache.get("a"));

        section("put d → should evict b (LRU)");
        cache.put("d", "D");
        System.out.println("  b → " + cache.get("b"));
        System.out.println("  a → " + cache.get("a"));
        System.out.println("  c → " + cache.get("c"));
        System.out.println("  d → " + cache.get("d"));

        section("getOrLoad single-flight");
        AtomicInteger calls = new AtomicInteger();
        CacheLoader<String, String> loader = k -> {
            calls.incrementAndGet();
            return "loaded-" + k;
        };
        System.out.println("  e → " + cache.getOrLoad("e", loader));
        System.out.println("  e → " + cache.getOrLoad("e", loader));
        System.out.println("  loader calls=" + calls.get() + " (expected 1)");

        section("Advance 11 minutes → TTL expires");
        now[0] = now[0].plus(Duration.ofMinutes(11));
        System.out.println("  e → " + cache.get("e"));     // miss; expired
        System.out.println("  d → " + cache.get("d"));     // miss; expired
        System.out.println("  size=" + cache.size());

        System.out.println("\n  " + cache.stats());

        section("ShardedLruCache");
        Cache<String, Integer> sharded = CacheBuilder.<String, Integer>newBuilder()
                .capacity(64).clock(clock).buildSharded(4);
        for (int i = 0; i < 100; i++) sharded.put("k" + i, i);
        int present = 0;
        for (int i = 0; i < 100; i++) if (sharded.get("k" + i).isPresent()) present++;
        System.out.println("  100 keys put, sharded total size=" + sharded.size() + " hits=" + present);
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
