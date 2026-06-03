# 13 · LRU Cache — Extensions & Tradeoffs

## Extensions

### 1. LFU (Least Frequently Used)
Each entry has a frequency counter. Evict the entry with the lowest count. Implementation choices:
- **Min-heap**: O(log N) per update; bad.
- **Buckets-of-DLLs** (LeetCode 460): one DLL per frequency level; O(1) per op.
- **Frequency sketch (Caffeine)**: count-min sketch + window LRU. Approximate but very fast.

LFU does better than LRU under power-law access (a few keys *very* hot, many keys cold). LRU forgets popular keys quickly during a burst of new keys.

### 2. ARC (Adaptive Replacement Cache)
Adapts between recency and frequency on the fly. Maintains four lists: T1 (recent), T2 (frequent), B1 (recent ghost), B2 (frequent ghost). Resizes T1/T2 based on which ghost list a key reappears on.

Patented at one point; Linux ZFS uses it.

### 3. W-TinyLFU (Caffeine)
Window LRU + frequency sketch (count-min) + main SLRU. Best hit ratio in benchmarks.

### 4. Weigher / byte-based capacity
`weigher((k, v) -> v.size)` → capacity in bytes. Useful when entry sizes vary.

### 5. Refresh-ahead
On read, if the entry is "near" expiry, schedule an async reload. Reduces miss-induced latency for predictable hot keys.

### 6. Negative caching
Cache "key not found" responses briefly. Prevents repeated DB hits for missing keys.

### 7. Async eviction listener
Run listener on a dedicated thread to avoid blocking writers.

### 8. Persistence on shutdown
Serialize live entries to disk; reload on startup. Useful for warm starts.

### 9. Distributed L2 (Redis adapter)
Wrap Redis behind `Cache<K, V>`. Two layers form a `HierarchicalCache`.

### 10. Invalidation broadcast
Pub/Sub fan-out to invalidate L1 across processes when source-of-truth changes.

## Tradeoffs

### LRU vs LFU vs W-TinyLFU

| Criterion | LRU | LFU (buckets) | W-TinyLFU |
| --- | --- | --- | --- |
| Hit ratio (uniform) | good | good | excellent |
| Hit ratio (Zipfian / power-law) | poor | excellent | excellent |
| Implementation | trivial | medium | hard |
| Memory overhead | minimal | medium | medium |
| **Default**: LRU is simple and good enough; **W-TinyLFU** for max hit ratio | | | |

### Single global lock vs sharded vs lock-free

| Criterion | Global lock | Sharded | Lock-free (Caffeine) |
| --- | --- | --- | --- |
| Read latency | poor under contention | good | excellent |
| Implementation | trivial | easy | hard |
| Hit ratio (LRU exact) | exact | shard-local exact | exact (via drain) |
| **Default**: Sharded; use Caffeine if you need max throughput | | | |

### TTL: lazy vs proactive sweeper

| Lazy | Proactive |
| --- | --- |
| Zero CPU when idle | Periodic CPU |
| Memory may hold expired entries | Memory recovered promptly |
| **Default**: lazy; sweeper if memory-bound |

### In-process vs distributed

| In-process | Distributed (Redis) |
| --- | --- |
| <1µs hits | ~100µs hits |
| Per-process; not coherent | Shared, coherent (with effort) |
| Limited by process memory | Limited by Redis size |
| **Default**: combine via L1 + L2 |

### Cache invalidation: TTL vs explicit vs versioned keys

| Strategy | Consistency | Complexity |
| --- | --- | --- |
| TTL only | Eventual | Trivial |
| TTL + DEL | Near-strong | Pub/Sub fan-out |
| Versioned keys | Strong | Moderate |
| Write-through | Strong on write | Bypass risk |
| **Default**: versioned keys for "must be correct"; TTL for "good enough"

## Open questions

- What's the access pattern? (Power-law → consider LFU/W-TinyLFU.)
- What's the consistency requirement? (Strong → versioned keys; eventual → TTL.)
- Are values heterogeneous in size? (Yes → byte-based capacity.)
- Do we need cross-process coherency? (Yes → L2 + Pub/Sub.)
- Is the loader expensive enough to warrant single-flight? (Almost always yes.)

## Output

```
Extensions: LFU, ARC, W-TinyLFU, weigher, refresh-ahead, negative caching,
            persistence, L2, invalidation broadcast
Tradeoffs:  policy choice, concurrency strategy, lazy vs sweeper, in-proc vs
            distributed, invalidation scheme
Pre-decided: HashMap+DLL, sharded for concurrency, lazy TTL, builder pattern,
             pluggable policy, single-flight loader
```
