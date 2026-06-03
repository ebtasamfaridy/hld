# 11 · LRU Cache — Concurrency & Scaling

## The fundamental problem

Even a "perfect" LRU has a concurrency tax: every `get` modifies the DLL (move-to-head). So reads are writers from a concurrency standpoint. Naive `synchronized(this)` serializes everything.

Three approaches to fix it:
1. **Striped locks (sharding)** — most common.
2. **Lock-free reads + batched DLL drain** — Caffeine's approach.
3. **Approximate LRU with sampling** — Redis's approach.

## 1. Striped locks (ShardedLruCache)

Split the cache into N shards. Hash the key to pick a shard. Each shard has its own lock. Distinct keys from different threads → no contention.

```
shardIdx = (hash(key) >>> 16) & (N - 1)   // power-of-two N
shard[shardIdx].get(key)                  // acquires shard lock
```

Tradeoffs:
- Simple, robust, works for any policy.
- Per-shard LRU; cache-wide eviction is approximate (each shard evicts independently).
- Capacity is split across shards; hot shards may evict more.

For most production use cases, this is enough.

## 2. Caffeine's approach (lock-free reads)

- Reads use ConcurrentHashMap (lock-free).
- Move-to-head events are recorded in a ring buffer (per-thread) without lock.
- A background "drain" task batches the events and updates the DLL serially.
- Eviction triggered when ring buffer fills or capacity is exceeded.

This decouples access recording from access reordering. Reads scale linearly with cores; writes are serialized but cheap (one drain thread).

Cost: complexity. Implementing ring buffers + drainer + correct ordering is hard.

## 3. Approximate LRU (Redis)

Redis's `allkeys-lru` doesn't actually maintain a perfect LRU list. It samples K random keys, evicts the least-recently-used among them. K=5 is the default; K=10 is much closer to true LRU.

Cost: O(K) per eviction; O(1) per access.
Quality: hit ratio close to real LRU at K=10.

This is the **right tradeoff at scale** — Redis values being O(1) on hot path more than achieving exact LRU.

## Comparison

| Approach | get latency | Hit ratio | Complexity |
| --- | --- | --- | --- |
| `synchronized` LRU | high under contention | exact | low |
| Sharded | low | exact within shard | low |
| Caffeine ring buffer | very low | exact | high |
| Redis sampled LRU | very low | ~95% of exact | medium |

## Memory bounding

Two flavors:
1. **Count-based**: max N entries. Simple. Misleading if value sizes vary.
2. **Byte-based**: weigher(K, V) returns size; capacity is bytes. Used when entry sizes vary widely.

Caffeine's approach: `maximumWeight()` + `weigher((k, v) -> ...)`.

## Stampede control

When a popular key expires and 1000 threads call `getOrLoad` simultaneously, **only one** should hit the source. The other 999 should wait for the first one's result.

Implementation:
```
ConcurrentHashMap<K, CompletableFuture<V>> inflight;
inflight.computeIfAbsent(key, k -> {
    return CompletableFuture.supplyAsync(() -> loader.load(k));
}).get();
```

After the future completes, remove from `inflight`.

## TTL sweeper

For very hot caches, expired entries hold memory until a `get` happens. Two approaches:
- **Lazy**: remove on `get`. Default. Saves CPU.
- **Proactive sweeper**: periodic thread scans. Adds CPU; recovers memory faster.
- **Hybrid (Redis-style)**: random sample on every op + periodic sweep.

## Distributed cache concurrency

When multiple processes share an L2 (Redis):
- Each process has its own L1.
- L1 is local; L2 shared.
- Invalidation: when one process mutates the source, it must invalidate L2 + broadcast to invalidate all peers' L1.
- Use Pub/Sub (Redis or Kafka) for fan-out.

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Loader exception | Don't cache; counter `loadsFailed++`; (optional) negative TTL cache |
| Listener exception | Catch & log; never break cache state |
| OOM | Trigger emergency eviction; alert |
| Lock contention | Detect via metrics (lock wait time); shard more |
| L2 down | Fall through to source; degraded mode |

## Output

```
Concurrency:  shard-by-key (default), Caffeine-style ring buffer (advanced),
              Redis-style sampled LRU (very large scale)
Memory:       count-based default; byte-weigher for variable values
Stampede:     CompletableFuture-keyed single-flight
TTL:          lazy default; sweeper for memory-bound workloads
Distributed:  L1 + L2 + Pub/Sub for invalidation; degrade gracefully
```
