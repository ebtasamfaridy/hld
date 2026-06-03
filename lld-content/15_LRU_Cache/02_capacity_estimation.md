# 02 · LRU Cache — Capacity Estimation

## Use case A: in-process cache

```
Process memory budget:    1 GB cache
Avg entry overhead:
  - Key (string, 32B)        + 24B header
  - Value reference          + 8B
  - Map.Entry / Node header  + ~64B
  - DLL prev/next pointers   + 16B
  Total per entry            ≈ 144B + value size
Avg value size:           1 KB
Capacity:                 ~1 GB / (1 KB + ~150B) ≈ 870 K entries
```

If values are small (e.g., 64 B), the per-entry overhead dominates and the cache can hold ~6 M entries — but each one is mostly bookkeeping. **Hence weigher-based capacity in production caches.**

## Use case B: distributed Redis L2

```
Redis instance:           64 GB RAM
Avg key + metadata:       ~80B
Avg value:                1 KB
Entries:                  ~60 M
Eviction policy:          allkeys-lru or volatile-lru
```

## Throughput

```
Hot service:              50 K req/s
Cache hits:               48 K (96%)
Cache misses:             2 K → DB
DB qps before cache:      50 K
DB qps after cache:       2 K
24× DB load reduction
```

## Concurrency

```
JVM threads serving traffic:  100
Cache ops/sec:                10 M (each thread doing 100 K)
Single global lock:           contention nightmare
Striped (16 stripes):         per-stripe lock; ~625 K ops/sec/stripe (fine)
ConcurrentHashMap-based:      lock-free for reads
```

## Memory bookkeeping

| Concern | Mitigation |
| --- | --- |
| Entry overhead exceeds value size | Use byte-weigher capacity |
| Strong refs prevent GC | Weak/soft reference values when appropriate |
| Hot keys cause large value creation | Reuse via interning or pooling |
| Long key strings | Intern or compress |

## What forces design

1. **O(1) requires hashing + DLL**. Non-negotiable for `get/put`.
2. **Concurrency without sharding is a bottleneck.** Either use ConcurrentHashMap-based or striped DLL segments.
3. **Eviction listener can be expensive**; run it asynchronously on a dedicated thread to avoid blocking the cache path.
4. **TTL eviction lazy**; proactive sweeper only if memory pressure is real.
5. **Loader is on a slow path**; ensure single-flight to avoid herd.

## Output

```
In-process:   1 GB → ~870K entries (1KB values)
Redis L2:     64 GB → 60M entries
Throughput:   10M ops/sec on 16-core, sharded
DB savings:   ~96% hit ratio → 25× DB load reduction
Bookkeeping:  per-entry ~150B; weigher needed for small values
```
