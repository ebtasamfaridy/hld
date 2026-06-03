package com.lru.store;

@FunctionalInterface
public interface EvictionListener<K, V> {
    void onEvict(K key, V value, Cause cause);
}
