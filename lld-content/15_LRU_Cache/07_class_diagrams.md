# 07 · LRU Cache — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Enums & functional interfaces =====
    class Cause {
      <<enumeration>>
      SIZE
      EXPIRED
      EXPLICIT
      REPLACED
    }
    class CacheLoader~K,V~ {
      <<functional interface>>
      +load(key) V
    }
    class EvictionListener~K,V~ {
      <<functional interface>>
      +onEvict(key, value, cause)
    }
    class Kind {
      <<enumeration>>
      LRU
      LFU
      FIFO
      MRU
      ARC
      W_TINY_LFU
    }

    %% ===== Strategy: EvictionPolicy =====
    class EvictionPolicy~K,V~ {
      <<interface>>
      +kind() Kind
    }
    class LruPolicy~K,V~ {
      +kind() Kind
    }
    EvictionPolicy <|.. LruPolicy

    %% ===== Internal node (DLL) =====
    class Node~K,V~ {
      ~K key
      ~V value
      ~long expiresAtEpochMillis
      ~Node prev
      ~Node next
      ~isExpired(nowMillis) boolean
    }

    %% ===== Stats =====
    class Stats {
      -LongAdder hits
      -LongAdder misses
      -LongAdder evictions
      -LongAdder loadsOk
      -LongAdder loadsFailed
      +hit()
      +miss()
      +evict()
      +loadOk()
      +loadFail()
      +hits() long
      +misses() long
      +evictions() long
      +hitRatio() double
    }

    %% ===== Cache abstraction =====
    class Cache~K,V~ {
      <<interface>>
      +get(key) Optional~V~
      +getOrLoad(key, loader) V
      +put(key, value)
      +put(key, value, ttl)
      +remove(key) Optional~V~
      +size() int
      +clear()
      +stats() Stats
    }

    %% ===== Single-shard LRU =====
    class LruCache~K,V~ {
      -int capacity
      -Duration defaultTtl
      -EvictionListener listener
      -Clock clock
      -Map~K,Node~ map
      -Node head
      -Node tail
      -ReentrantLock lock
      -Stats stats
      -ConcurrentMap~K,CompletableFuture~ inflightLoads
      +get(key) Optional~V~
      +getOrLoad(key, loader) V
      +put(key, value)
      +put(key, value, ttl)
      +remove(key) Optional~V~
      +size() int
      +clear()
      +stats() Stats
      -evictLruIfFull()
      -touch(node)
    }
    Cache <|.. LruCache
    LruCache *-- "*" Node
    LruCache *-- "1" Stats
    LruCache o-- "1" EvictionListener

    %% ===== Sharded composition =====
    class ShardedLruCache~K,V~ {
      -Cache[] shards
      -int mask
      -Stats aggregate
      -shardFor(key) Cache
      +get(key) Optional~V~
      +getOrLoad(key, loader) V
      +put(key, value)
      +put(key, value, ttl)
      +remove(key) Optional~V~
      +size() int
      +clear()
      +stats() Stats
    }
    Cache <|.. ShardedLruCache
    ShardedLruCache o-- "*" Cache : shards

    %% ===== Builder =====
    class CacheBuilder~K,V~ {
      -int capacity
      -Duration ttl
      -EvictionListener listener
      -Clock clock
      -int shards
      +newBuilder() CacheBuilder$
      +capacity(n) CacheBuilder
      +ttl(d) CacheBuilder
      +evictionListener(l) CacheBuilder
      +clock(c) CacheBuilder
      +shards(n) CacheBuilder
      +build() Cache
    }
    CacheBuilder ..> Cache
    CacheBuilder ..> LruCache
    CacheBuilder ..> ShardedLruCache
```

---



## Core class diagram

```mermaid
classDiagram
    class Cache~K,V~ {
      <<interface>>
      +get(key) Optional~V~
      +getOrLoad(key, loader) V
      +put(key, value)
      +put(key, value, ttl)
      +remove(key) Optional~V~
      +clear()
      +size() int
      +stats() Stats
    }

    class LruCache~K,V~ {
      -capacity: int
      -map: ConcurrentHashMap~K,Node~
      -head, tail: Node
      -policy: EvictionPolicy
      -ttl: Duration
      -listener: EvictionListener
      -stats: Stats
      -lock: ReentrantLock
    }

    class Node~K,V~ {
      -key
      -value
      -prev, next: Node
      -expiresAt: long
      -frequency: int
    }

    class EvictionPolicy {
      <<interface>>
      +onAccess(node)
      +onWrite(node)
      +pickVictim(head, tail) Node
    }
    class LruPolicy
    class LfuPolicy
    class FifoPolicy
    EvictionPolicy <|.. LruPolicy
    EvictionPolicy <|.. LfuPolicy
    EvictionPolicy <|.. FifoPolicy

    class CacheLoader~K,V~ { <<interface>> +load(key) V }
    class EvictionListener~K,V~ { <<interface>> +onEvict(k, v, cause) }
    class Stats { +hits, misses, evictions, hitRatio }
    class CacheBuilder { +capacity, ttl, policy, listener +build() }

    Cache <|.. LruCache
    LruCache o-- Node
    LruCache o-- EvictionPolicy
    LruCache o-- EvictionListener
    LruCache o-- Stats
    LruCache ..> CacheLoader
    CacheBuilder ..> LruCache
```

## Sharded cache (production-grade)

```mermaid
classDiagram
    class ShardedLruCache~K,V~ {
      -shards: Cache[]
      +get(key) Optional~V~
      +put(key, value)
    }
    class Cache~K,V~
    ShardedLruCache "1" o-- "16" Cache : delegates by hash(key) mod N
```

Each shard is a self-contained LRU with its own lock. Distinct keys → distinct shards → no contention.

## L1 + L2 hierarchy

```mermaid
classDiagram
    class HierarchicalCache~K,V~ {
      -l1: Cache
      -l2: DistributedCache
      -loader: CacheLoader
      +get(key) Optional~V~
      +put(key, value)
    }
    class DistributedCache~K,V~ {
      <<interface>>
      +get(key) Optional~V~
      +put(key, value, ttl)
    }
    class RedisCache
    DistributedCache <|.. RedisCache
```

## Package layout (`com.lru`)

```
cache/         Cache, LruCache, Node, CacheBuilder, ShardedLruCache, HierarchicalCache, Stats
policy/        EvictionPolicy, LruPolicy, LfuPolicy, FifoPolicy
store/         CacheLoader, EvictionListener
distributed/   DistributedCache, RedisCache (stub)
```

## Why these abstractions

### Cache as an interface
Lets users program against the contract. Tests can use a no-op cache. Wrappers (instrumented cache, sharded cache, hierarchical cache) implement the same interface.

### EvictionPolicy as Strategy
The LRU/LFU split runs deep — different data structures internally. Hiding it behind one method (`pickVictim`) lets the cache class stay simple while supporting any policy.

### CacheLoader as a functional interface
Caller supplies behavior at call site. Encourages closure-based loaders.

### EvictionListener as a functional interface
Same story; runs callbacks without coupling.

### Sharding as composition
A `ShardedLruCache` *contains* N `LruCache` instances and dispatches by hash. The DLL inside each shard is small (capacity / N), so eviction is efficient and locks are short.

### Hierarchical as composition
An `HierarchicalCache` composes an L1 + L2. Reads cascade. Writes propagate. Misses populate up.

## Output

```
Library hierarchy:
  Cache (interface)
    ↳ LruCache (basic)
    ↳ ShardedLruCache (concurrency)
    ↳ HierarchicalCache (L1 + L2)
Strategy:        EvictionPolicy
Functional:      CacheLoader, EvictionListener
Builder:         CacheBuilder for rich config
```
