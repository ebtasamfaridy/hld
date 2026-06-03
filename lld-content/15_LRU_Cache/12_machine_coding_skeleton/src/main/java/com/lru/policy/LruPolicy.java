package com.lru.policy;

public final class LruPolicy<K, V> implements EvictionPolicy<K, V> {
    @Override public Kind kind() { return Kind.LRU; }
}
