package com.lru.cache;

import com.lru.store.CacheLoader;

import java.time.Duration;
import java.util.Optional;

public interface Cache<K, V> {
    Optional<V> get(K key);
    V getOrLoad(K key, CacheLoader<K, V> loader);
    void put(K key, V value);
    void put(K key, V value, Duration ttl);
    Optional<V> remove(K key);
    int size();
    void clear();
    Stats stats();
}
