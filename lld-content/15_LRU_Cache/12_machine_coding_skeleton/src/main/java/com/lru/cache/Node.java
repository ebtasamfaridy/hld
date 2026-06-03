package com.lru.cache;

final class Node<K, V> {
    K key;
    V value;
    long expiresAtEpochMillis;     // Long.MAX_VALUE = no TTL
    Node<K, V> prev, next;

    Node(K key, V value, long expiresAtEpochMillis) {
        this.key = key;
        this.value = value;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
    }

    boolean isExpired(long nowMillis) {
        return expiresAtEpochMillis != Long.MAX_VALUE && nowMillis >= expiresAtEpochMillis;
    }
}
