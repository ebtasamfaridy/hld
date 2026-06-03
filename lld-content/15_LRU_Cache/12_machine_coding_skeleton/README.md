# 12 · LRU Cache — Machine Coding Skeleton

In-process cache with pluggable eviction policy, TTL, single-flight loader, and listener.

```
src/main/java/com/lru/
├── cache/         Cache, LruCache, Node, CacheBuilder, ShardedLruCache, Stats
├── policy/        EvictionPolicy, LruPolicy (LFU, FIFO stubs)
├── store/         CacheLoader, EvictionListener, Cause
├── distributed/   (placeholder for L2 Redis adapter)
└── Main.java
```

## Demo

1. Build cache cap=3, TTL=10 min.
2. put a, b, c → full.
3. get a → moves to MRU.
4. put d → evicts b (LRU).
5. getOrLoad("e", loader) → loader runs once; second call returns cached.
6. simulate TTL expiry; expired key returns miss.
7. show ShardedLruCache distributing keys.
