package com.lru.store;

@FunctionalInterface
public interface CacheLoader<K, V> {
    V load(K key) throws Exception;
}
