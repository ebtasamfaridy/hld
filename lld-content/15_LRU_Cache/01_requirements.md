# 01 · LRU Cache — Requirements

## Functional requirements

### Core
- `get(key) → value | miss`
- `put(key, value)` — set or replace
- `remove(key)`
- Capacity-bounded; least-recently-used entry evicted when full
- O(1) `get` and `put`

### Required extensions (interview reality)
- **TTL**: per-entry or cache-wide expiry
- **Pluggable policy**: LRU, LFU, FIFO (Strategy)
- **Stats**: hit/miss/eviction counts; hit ratio
- **Loader function**: `getOrLoad(key, loader)` — Caffeine-style
- **Concurrent access** from many threads (read-mostly workload)
- **Stampede protection**: only one loader per missing key
- **Listener / callback** on eviction

### V2 extensions
- Byte-bounded capacity (weigher per entry)
- Write-through to a backing store (e.g., DB)
- Distributed L2 (Redis)
- Persistence on shutdown

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| `get` latency p99 | < 1 µs (in-process) | Caches must be cheap |
| `put` latency p99 | < 5 µs | Same |
| Concurrent throughput | 10 M ops/sec on 16 cores | Hot path |
| Hit ratio | > 90 % for typical workloads | Whole point of caching |
| Memory footprint | bounded; no leaks | Long-running processes |

## Actors

```
Client          - reads/writes via Cache API
Loader          - function that fetches a value on miss (DB, RPC, etc.)
EvictionListener- consumer notified on eviction
Backing store   - DB / external API (only when write-through enabled)
```

## Edge cases

| Case | Handling |
| --- | --- |
| `put` of existing key | Update value, move to MRU end (LRU policy) |
| `get` of expired entry | Treat as miss; evict eagerly |
| Two threads `getOrLoad` same missing key | Only one loader runs (single-flight) |
| Eviction during a concurrent `get` | Atomic; no partial state |
| Capacity 0 | Always evict immediately (degenerate) |
| Loader throws | Propagate; do NOT cache the exception (or cache it briefly with negative TTL) |
| Same key updated by many threads | Last write wins; no in-flight merge |
| Key with `null` value | Reject (use Optional or special sentinel) |
| Memory pressure | Optional weigher + capacity in bytes |

## V1 vs V2 scope

| Feature | V1 | V2 |
| --- | --- | --- |
| Get/Put/Remove with O(1) | ✓ | |
| LRU policy | ✓ | |
| TTL | basic | per-entry weights |
| Stats (hit/miss) | ✓ | |
| Single-flight loader | ✓ | |
| Eviction listener | ✓ | |
| LFU / ARC / W-TinyLFU | | ✓ |
| Sharded for concurrency | basic | striped, lock-free where possible |
| Byte-bounded capacity | | ✓ |
| L2 Redis | | ✓ |
| Persistence | | ✓ |

## Output

```
Core:    get, put, remove, capacity-bounded LRU; O(1)
NFR:     <1µs p99 get; high concurrent throughput
Edge:    update-existing, expired-entry, single-flight loader, listener
V1:      LRU + TTL + stats + loader + listener
V2:      LFU/ARC, sharding, byte capacity, L2 cache, persistence
```
