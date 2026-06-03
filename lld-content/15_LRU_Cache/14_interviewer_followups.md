# 14 · LRU Cache — Interviewer Follow-ups

## Q1. "Why HashMap + Doubly Linked List?"

Two requirements: O(1) lookup and O(1) reorder.
- HashMap: O(1) lookup by key.
- Doubly linked list: O(1) move-to-head or remove-tail given a node reference.

A singly linked list forces O(N) to find the previous node when removing. A list alone (no map) forces O(N) to find a key. Both data structures together → O(1) for both ops.

---

## Q2. "Why move on `get`?"

LRU = least-recently-used. A successful `get` makes that key the most recently used. Moving it to the head means the tail naturally drifts toward "least used." Eviction = remove tail.

---

## Q3. "What if I use `LinkedHashMap` with `accessOrder=true`?"

Java's `LinkedHashMap` does exactly this — it's a HashMap + DLL. You override `removeEldestEntry()` to bound the cache.

```java
LinkedHashMap<K, V> cache = new LinkedHashMap<>(16, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
};
```

It's correct and concise. But:
- Not thread-safe (must wrap in `Collections.synchronizedMap` or use a lock).
- Doesn't expose stats, listener, single-flight loader.
- Doesn't let you swap in LFU later.

Real interviewers want the manual implementation to verify you understand the algorithm, then accept LinkedHashMap as the production answer for simple cases.

---

## Q4. "How do you make this thread-safe?"

Three levels:
1. **Single ReentrantLock** — simplest. Bottleneck under load.
2. **Sharded** — split into N caches by `hash(key) mod N`. Distinct keys don't contend.
3. **Caffeine-style** — ConcurrentHashMap reads + ring buffer for access events + drainer thread updates the DLL.

Sharded is the right default. Caffeine is what you reach for when sharding isn't enough.

---

## Q5. "Two threads call `getOrLoad(K)` simultaneously and key is missing. Loader is expensive (DB read). What happens?"

Without protection, both call the loader → herd / stampede / wasted work.

Single-flight: per-key lock or `CompletableFuture` keyed on the key. First thread runs the loader; second thread waits, sees the populated value, returns it without calling the loader.

Implementation:
```java
ConcurrentHashMap<K, CompletableFuture<V>> inflight = ...;
return inflight.computeIfAbsent(key, k ->
    CompletableFuture.supplyAsync(() -> loader.load(k))
).get();
```

---

## Q6. "Loader throws an exception. Behavior?"

Don't cache the exception. Increment `loadsFailed`. Propagate the exception to caller.

Optionally: cache the failure briefly (negative caching) to prevent hammering the source. E.g., cache "loader failed" for 1 second. Useful when failures are persistent (DB down) and many threads ask for the same key.

---

## Q7. "Walk through what happens on `put(K, V)` for an existing key."

1. Lookup K in the map → existing Node.
2. Update Node's value (and `expiresAt` if TTL changed).
3. Move Node to head (it's now MRU).
4. Fire eviction listener with cause = REPLACED.

We don't increment size; nothing is evicted. The list still has all the same nodes.

---

## Q8. "How is TTL handled?"

Each node has `expiresAt`. Two strategies:
- **Lazy**: on `get`, check; if expired, evict and return miss.
- **Proactive sweeper**: periodic thread iterates and removes expired entries.

Lazy is enough for most workloads. Proactive is needed only if expired entries occupy enough memory to matter.

---

## Q9. "What's wrong with using LRU when 1% of keys account for 90% of traffic?"

A burst of misses (e.g., scanning over many cold keys) can flush hot keys out of the cache, even though they're more "valuable." LRU has no notion of frequency.

Better policies for power-law: LFU (counts), W-TinyLFU (frequency sketch), or 2Q / SLRU (segmented).

---

## Q10. "How would you turn this into a distributed cache?"

Move the cache to Redis. Each app process has a thin client. Misses fall through to the source (DB).

For best performance: keep an in-process L1 (small, fast); fall through to Redis L2; fall through to DB. Invalidation: when DB updates, publish to a Pub/Sub channel; all L1 caches subscribe and remove the affected key.

---

## Q11. "Cache invalidation is one of the hardest problems in CS. Why?"

When source-of-truth changes, the cache holds stale data. Strategies:
- **TTL**: stale up to TTL seconds. Simple. Eventual consistency.
- **Explicit DEL**: writer updates DB then DELs cache. Race: another reader may re-populate stale value between DEL and DB-commit. Use "double-delete" pattern (DEL → write → DEL again after small delay).
- **Versioned keys**: every record carries a version; cache key includes version. Old keys age out. Strong consistency.
- **Write-through**: app writes to cache + DB atomically. Bypasses if other writers exist.

Each has tradeoffs. The right one depends on consistency requirements.

---

## Q12. "Memory bounded by count or bytes?"

Count is simple: `if (size() > capacity) evict()`.

Bytes is better when values vary in size: `weigher(K, V) → bytes; while (totalWeight > capacityBytes) evict()`. Caffeine, Guava all support this.

---

## Q13. "Eviction listener throws. Now what?"

Catch and log. Never let a listener exception break the cache state. Otherwise a misbehaving listener can corrupt the data structure (half-removed entry, etc.).

For listeners that may be slow, run them async on a dedicated thread.

---

## Q14. "How do you test this?"

- **Functional**: standard get/put/remove with assertions.
- **LRU correctness**: insert N+1 items into capacity N, assert oldest evicted.
- **TTL**: virtual clock; assert expired entries are misses.
- **Concurrency**: 16 threads × 1M ops; assert size never exceeds capacity, no `null` returns from concurrent gets, hit ratio matches expectation.
- **Single-flight**: 100 threads call `getOrLoad` for same key; assert loader called exactly once.
- **Listener**: assert exactly N callbacks for N evictions.

Property-based tests catch ordering bugs that example tests miss.

---

## Q15. "What's the one bug interviewees usually miss?"

Forgetting to **also remove** from the HashMap when removing from the DLL on eviction. The map keeps a stale reference; future gets find it and return wrong data or crash.

The two structures must always be modified together. Wrap the operation in a helper:

```java
private void unlinkAndRemove(Node n) {
    removeFromList(n);
    map.remove(n.key);
}
```

---

## Output

```
Drilled:
- Why HashMap + DLL (O(1) lookup + O(1) reorder)
- Why move on get (LRU semantics)
- LinkedHashMap shortcut and its limits
- Concurrency strategies (lock, shard, lock-free)
- Single-flight loader (stampede protection)
- Loader failure handling (negative caching optional)
- put-existing flow (REPLACED cause)
- TTL: lazy vs sweeper
- LRU's weakness on power-law (consider LFU/W-TinyLFU)
- Distributed cache patterns (L1 + L2 + Pub/Sub)
- Cache invalidation strategies
- Bytes vs count capacity
- Listener exception handling
- Test strategy (functional + concurrency + property)
- Common bug (forgetting to remove from map on DLL evict)
```
