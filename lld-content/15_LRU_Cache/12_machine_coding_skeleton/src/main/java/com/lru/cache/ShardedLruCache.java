package com.lru.cache;

import com.lru.store.CacheLoader;

import java.time.Duration;
import java.util.Optional;

/** Routes operations to one of N inner caches based on key hash. */
public final class ShardedLruCache<K, V> implements Cache<K, V> {

    private final Cache<K, V>[] shards;
    private final int mask;
    private final Stats aggregate = new Stats();   // optional aggregate (not wired here)

    public ShardedLruCache(Cache<K, V>[] shards) {
        this.shards = shards;
        this.mask = shards.length - 1;             // shards.length is power of 2
    }

    private Cache<K, V> shardFor(K key) {
        int h = key.hashCode();
        h ^= (h >>> 16);                            // spread
        return shards[h & mask];
    }

    @Override public Optional<V> get(K key)                       { return shardFor(key).get(key); }
    @Override public V getOrLoad(K key, CacheLoader<K, V> loader) { return shardFor(key).getOrLoad(key, loader); }
    @Override public void put(K key, V value)                     { shardFor(key).put(key, value); }
    @Override public void put(K key, V value, Duration ttl)       { shardFor(key).put(key, value, ttl); }
    @Override public Optional<V> remove(K key)                    { return shardFor(key).remove(key); }

    @Override public int size() {
        int s = 0;
        for (Cache<K, V> shard : shards) s += shard.size();
        return s;
    }

    @Override public void clear() { for (Cache<K, V> shard : shards) shard.clear(); }
    @Override public Stats stats() { return aggregate; }   // each shard also has its own
}
