package com.lru.policy;

/**
 * Strategy interface for eviction. The current LruCache hard-codes LRU via
 * the doubly-linked list; alternative policies (LFU, FIFO, ARC) would either
 * implement this and the cache would delegate to them, or be implemented as
 * separate cache classes.
 */
public interface EvictionPolicy<K, V> {
    enum Kind { LRU, LFU, FIFO, MRU, ARC, W_TINY_LFU }

    Kind kind();
}
