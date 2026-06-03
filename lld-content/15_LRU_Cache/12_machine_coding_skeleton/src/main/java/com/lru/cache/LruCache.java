package com.lru.cache;

import com.lru.store.Cause;
import com.lru.store.CacheLoader;
import com.lru.store.EvictionListener;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-shard LRU. HashMap + Doubly Linked List → O(1) get/put.
 * Reads and writes are serialized by a single ReentrantLock per cache.
 * For higher concurrency, use ShardedLruCache.
 */
public final class LruCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Duration defaultTtl;       // null = no TTL
    private final EvictionListener<K, V> listener;
    private final Stats stats = new Stats();
    private final Clock clock;

    private final Map<K, Node<K, V>> map = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null, 0); // sentinel: MRU side
    private final Node<K, V> tail = new Node<>(null, null, 0); // sentinel: LRU side
    private final ReentrantLock lock = new ReentrantLock();

    /** Per-key locks for single-flight loaders. */
    private final ConcurrentHashMap<K, Object> loaderLocks = new ConcurrentHashMap<>();

    LruCache(int capacity, Duration defaultTtl,
             EvictionListener<K, V> listener, Clock clock) {
        this.capacity = capacity;
        this.defaultTtl = defaultTtl;
        this.listener = listener == null ? (k, v, c) -> {} : listener;
        this.clock = Objects.requireNonNull(clock);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            Node<K, V> n = map.get(key);
            long now = clock.millis();
            if (n == null) { stats.miss(); return Optional.empty(); }
            if (n.isExpired(now)) {
                removeNode(n);
                map.remove(key);
                stats.miss();
                stats.evict();
                fire(n.key, n.value, Cause.EXPIRED);
                return Optional.empty();
            }
            moveToHead(n);
            stats.hit();
            return Optional.of(n.value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V getOrLoad(K key, CacheLoader<K, V> loader) {
        Objects.requireNonNull(key); Objects.requireNonNull(loader);
        Optional<V> v = get(key);
        if (v.isPresent()) return v.get();

        Object perKey = loaderLocks.computeIfAbsent(key, k -> new Object());
        synchronized (perKey) {
            v = get(key);
            if (v.isPresent()) {
                loaderLocks.remove(key, perKey);
                return v.get();
            }
            try {
                V loaded = loader.load(key);
                if (loaded == null) throw new IllegalStateException("loader returned null for " + key);
                put(key, loaded);
                stats.loadOk();
                return loaded;
            } catch (Exception e) {
                stats.loadFail();
                throw new RuntimeException("loader failed for key " + key, e);
            } finally {
                loaderLocks.remove(key, perKey);
            }
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key); Objects.requireNonNull(value);
        long expiresAt = (ttl == null) ? Long.MAX_VALUE : clock.millis() + ttl.toMillis();

        lock.lock();
        try {
            Node<K, V> existing = map.get(key);
            if (existing != null) {
                V old = existing.value;
                existing.value = value;
                existing.expiresAtEpochMillis = expiresAt;
                moveToHead(existing);
                fire(key, old, Cause.REPLACED);
                return;
            }
            Node<K, V> node = new Node<>(key, value, expiresAt);
            addAfterHead(node);
            map.put(key, node);

            while (map.size() > capacity) {
                Node<K, V> victim = tail.prev;
                if (victim == head) break;
                removeNode(victim);
                map.remove(victim.key);
                stats.evict();
                fire(victim.key, victim.value, Cause.SIZE);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<V> remove(K key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            Node<K, V> n = map.remove(key);
            if (n == null) return Optional.empty();
            removeNode(n);
            fire(key, n.value, Cause.EXPLICIT);
            return Optional.of(n.value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try { return map.size(); }
        finally { lock.unlock(); }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    @Override public Stats stats() { return stats; }

    private void moveToHead(Node<K, V> n) {
        removeNode(n);
        addAfterHead(n);
    }

    private void addAfterHead(Node<K, V> n) {
        n.prev = head;
        n.next = head.next;
        head.next.prev = n;
        head.next = n;
    }

    private void removeNode(Node<K, V> n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
        n.prev = null; n.next = null;
    }

    private void fire(K k, V v, Cause c) {
        try { listener.onEvict(k, v, c); }
        catch (RuntimeException e) { /* never break cache state */ }
    }
}
