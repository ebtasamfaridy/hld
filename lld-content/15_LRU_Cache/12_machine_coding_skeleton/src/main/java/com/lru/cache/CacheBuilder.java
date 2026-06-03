package com.lru.cache;

import com.lru.store.EvictionListener;

import java.time.Clock;
import java.time.Duration;

public final class CacheBuilder<K, V> {

    private int capacity = 1024;
    private Duration ttl = null;
    private EvictionListener<K, V> listener = null;
    private Clock clock = Clock.systemUTC();

    public static <K, V> CacheBuilder<K, V> newBuilder() { return new CacheBuilder<>(); }

    public CacheBuilder<K, V> capacity(int n) {
        if (n <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = n; return this;
    }
    public CacheBuilder<K, V> ttl(Duration d)                        { this.ttl = d; return this; }
    public CacheBuilder<K, V> evictionListener(EvictionListener<K, V> l) { this.listener = l; return this; }
    public CacheBuilder<K, V> clock(Clock c)                         { this.clock = c; return this; }

    public Cache<K, V> build() {
        return new LruCache<>(capacity, ttl, listener, clock);
    }

    /** Build a sharded cache; total capacity divided across N shards. */
    public Cache<K, V> buildSharded(int shards) {
        if (Integer.bitCount(shards) != 1) throw new IllegalArgumentException("shards must be power of 2");
        int per = Math.max(1, capacity / shards);
        @SuppressWarnings("unchecked")
        Cache<K, V>[] arr = (Cache<K, V>[]) new Cache[shards];
        for (int i = 0; i < shards; i++) {
            arr[i] = new LruCache<>(per, ttl, listener, clock);
        }
        return new ShardedLruCache<>(arr);
    }
}
