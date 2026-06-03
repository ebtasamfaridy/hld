# 10 · LRU Cache — Design Patterns

## 1. Strategy — `EvictionPolicy`
LRU, LFU, FIFO, MRU, ARC, W-TinyLFU. Same interface; cache delegates.

## 2. Builder — `CacheBuilder`
Configuration is rich (capacity, TTL, policy, listener, stats). Constructor explosion is the alternative; builder always wins.

## 3. Decorator — instrumentation, write-through, refresh-ahead
- `StatsCache(inner)` records metrics around `get`/`put`.
- `WriteThroughCache(inner, store)` writes to underlying store on put.
- `RefreshAheadCache(inner, loader, refreshAfter)` reloads near expiry.

Each decorator wraps the same `Cache<K,V>` interface.

## 4. Composite — `ShardedLruCache`, `HierarchicalCache`
Multiple inner caches treated uniformly via the same interface.

## 5. Functional / Lambda — `CacheLoader`, `EvictionListener`
Single-method interfaces; encourage closure-based use.

## 6. Memento — TTL expiry
Each entry remembers its `expiresAt`; on access we compare to now. The "memento" is the timestamp.

## 7. Observer — `EvictionListener`
Notified on every eviction; many listeners can be registered (V2).

## 8. Object pool (advanced) — Node reuse
On heavy churn, recycle Node objects to reduce GC pressure. Real Caffeine does this via a per-thread cache of free nodes.

## 9. Single-flight pattern (concurrency) — `getOrLoad`
Only one thread per missing key invokes the loader; others wait then read populated value.

## 10. CAS / Lock-free reads
ConcurrentHashMap-style concurrent reads on `get`; only the move-to-head path needs ordering. In the simplest implementation, the DLL operations are protected by a single lock. Caffeine uses a "ring buffer" trick to record access events without contending on the LRU list — DLL is updated by a background drain thread.

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| Inheritance for policies | Strategy is cleaner; lets caches change policy without recompile |
| Singleton cache | Process can have many caches with different configs |
| `synchronized` everywhere | One global lock kills throughput; shard or fine-grained |
| Custom hashtable | Use ConcurrentHashMap; battle-tested |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | EvictionPolicy | Pluggable victim selection |
| Builder | CacheBuilder | Rich, optional configuration |
| Decorator | Stats / WriteThrough / RefreshAhead | Composable cross-cutting concerns |
| Composite | Sharded / Hierarchical | Combine many inner caches |
| Functional | CacheLoader, Listener | SAM interfaces; lambda-friendly |
| Memento | TTL expires-at | Per-entry expiry comparison |
| Observer | EvictionListener | Notify on eviction |
| Single-flight | getOrLoad | Stampede protection |

## Output

```
Strategy:    EvictionPolicy (the heart of pluggability)
Builder:     CacheBuilder for config
Decorator:   Stats / WriteThrough / RefreshAhead wrappers
Composite:   Sharded + Hierarchical caches via same interface
Concurrency: single-flight for loaders; lock-free reads where possible
```
