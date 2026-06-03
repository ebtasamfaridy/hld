# 06 · LRU Cache — API Design

The LRU cache is a **library API**, not a network API. We design it like Caffeine / Guava.

## Core API

```java
public interface Cache<K, V> {
    Optional<V> get(K key);
    V getOrLoad(K key, CacheLoader<K, V> loader);
    void put(K key, V value);
    void put(K key, V value, Duration ttl);
    Optional<V> remove(K key);
    void clear();
    int size();
    Stats stats();
}
```

### Decisions

- **`Optional<V>` not `null`** — explicit miss vs present, no NPE.
- **Two `put` overloads** — one with default TTL, one with explicit per-entry TTL. Overloading not method-spaghetti because TTL is structurally distinct.
- **`getOrLoad`** — read-through with single-flight; the production pattern. `get` remains for fast-path reads.

## Builder pattern (Caffeine-style)

```java
Cache<String, Movie> cache = Cache.<String, Movie>newBuilder()
    .capacity(10_000)
    .ttl(Duration.ofMinutes(10))
    .evictionPolicy(EvictionPolicy.LRU)
    .evictionListener((k, v, cause) -> log.info("evicted {} ({})", k, cause))
    .stats(true)
    .build();
```

Configuration is rich; constructor explosion is the alternative. Builder always wins here.

## Loader API

```java
public interface CacheLoader<K, V> {
    V load(K key) throws Exception;
}
```

Caller supplies the loader at `getOrLoad` time. Loader exceptions propagate; a special `negative-cache` decorator can cache failures briefly to avoid hammering the source.

## Eviction listener API

```java
public interface EvictionListener<K, V> {
    void onEvict(K key, V value, Cause cause);
    enum Cause { SIZE, EXPIRED, EXPLICIT, REPLACED }
}
```

Run async by default; user can opt-in to sync if they need ordering guarantees with the cache write.

## Stats API

```java
public final class Stats {
    long hits();
    long misses();
    long evictions();
    long loadsSuccessful();
    long loadsFailed();
    long totalLoadTimeNs();
    double hitRatio();
}
```

Use `LongAdder` internally for high-throughput counter increments under contention.

## Concurrency contract

| Operation | Guarantee |
| --- | --- |
| `get` | Wait-free for hits; uses ConcurrentHashMap-like structure |
| `put` | Atomic; one winning version |
| `remove` | Atomic; returns the previous value |
| `getOrLoad` | Single-flight per key |
| Iteration | Weakly consistent (no fail-fast); OK for stats / debugging |

## Errors

| Class | When |
| --- | --- |
| `IllegalArgumentException` | null key / null value (we reject nulls explicitly) |
| `LoadingException` | Loader threw |
| `IllegalStateException` | After `close()`, calls fail |

## Output

```
Library API:    get / getOrLoad / put / remove / clear / size / stats
Builder:        capacity, ttl, evictionPolicy, listener, stats toggle
Loader:         CacheLoader<K,V>, single-flight at getOrLoad
Listener:       (key, value, cause); async by default
Stats:          LongAdder counters; computed hit ratio
Concurrency:    wait-free reads, atomic writes, weakly consistent iteration
```
