# 08 · LRU Cache — Sequence Diagrams

## 1. get(key) — hit path (single-threaded view)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cache as LruCache
    participant Map
    participant DLL

    App->>Cache: get(K)
    Cache->>Map: lookup K
    Map-->>Cache: Node
    alt expired
      Cache->>Cache: evict Node, miss
      Cache-->>App: Optional.empty
    else alive
      Cache->>DLL: moveToHead(Node)
      Cache-->>App: Optional(value)
      Cache->>Cache: stats.hit++
    end
```

## 2. put(key, value)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cache
    participant Map
    participant DLL
    participant Listener

    App->>Cache: put(K, V)
    Cache->>Map: lookup K
    alt exists
      Map-->>Cache: Node
      Cache->>Cache: update Node.value, expiresAt
      Cache->>DLL: moveToHead
    else new
      Cache->>Cache: new Node(K, V)
      Cache->>DLL: addToHead
      Cache->>Map: put K → Node
      alt size > capacity
        Cache->>DLL: removeTail() → victim
        Cache->>Map: remove victim.K
        Cache->>Cache: stats.eviction++
        Cache--)Listener: onEvict(victim.K, victim.V, SIZE)
      end
    end
```

## 3. getOrLoad — miss + single-flight

```mermaid
sequenceDiagram
    autonumber
    participant T1 as Thread1
    participant T2 as Thread2
    participant Cache
    participant Loader

    T1->>Cache: getOrLoad(K, loader)
    T1->>Cache: lookup → miss
    T1->>Cache: lock(K) [acquired]

    T2->>Cache: getOrLoad(K, loader)
    T2->>Cache: lookup → miss
    T2->>Cache: lock(K) [waits]

    T1->>Loader: load(K)
    Loader-->>T1: V
    T1->>Cache: put(K, V)
    T1->>Cache: unlock(K)
    T1-->>T1: return V

    T2->>Cache: lock(K) [acquired after T1]
    T2->>Cache: lookup → HIT (V populated by T1)
    T2->>Cache: unlock(K)
    T2-->>T2: return V (no second loader call)
```

## 4. TTL eviction (lazy)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cache
    participant Listener

    App->>Cache: get(K)
    Cache->>Cache: lookup → expiresAt < now
    Cache->>Cache: remove from map + DLL
    Cache--)Listener: onEvict(K, V, EXPIRED)
    Cache-->>App: Optional.empty
```

## 5. TTL eviction (proactive sweeper)

```mermaid
sequenceDiagram
    autonumber
    participant Sweep as Sweeper Thread
    participant Cache
    participant Listener

    loop every 1s
        Sweep->>Cache: purgeExpired()
        Cache->>Cache: scan TTL index
        Cache->>Cache: remove expired entries
        Cache--)Listener: onEvict each, EXPIRED
    end
```

## 6. ShardedLruCache — get path

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Sharded as ShardedLruCache
    participant Shard as LruCache (shard 7)

    App->>Sharded: get(K)
    Sharded->>Sharded: shardIdx = hash(K) mod 16 = 7
    Sharded->>Shard: get(K)
    Shard-->>Sharded: Optional(value)
    Sharded-->>App: Optional(value)
```

## 7. Hierarchical L1 + L2 — get path

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant H as HierarchicalCache
    participant L1
    participant L2 as Redis L2
    participant DB

    App->>H: get(K)
    H->>L1: get(K)
    alt L1 hit
      L1-->>H: V
      H-->>App: V
    else L1 miss
      H->>L2: get(K)
      alt L2 hit
        L2-->>H: V
        H->>L1: put(K, V)
        H-->>App: V
      else L2 miss
        H->>DB: load(K)
        DB-->>H: V
        H->>L2: put(K, V, ttl)
        H->>L1: put(K, V)
        H-->>App: V
      end
    end
```

## 8. Hierarchical write — propagate invalidation

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant H
    participant L1
    participant L2
    participant Bus as Pub/Sub

    App->>H: put(K, V) [or remove(K)]
    H->>L2: put(K, V, ttl) [or DEL]
    H->>L1: put(K, V)
    H->>Bus: publish "invalidate K"
    Note over Bus: other app's L1 listens, removes K locally
```

## Output

```
Get hit:        lookup → moveToHead, O(1)
Put new:        addToHead → if over-cap, removeTail + listener
getOrLoad:      double-checked locking; only one loader runs
TTL:            lazy on-read; proactive sweeper for memory pressure
Sharded:        hash-route to inner cache; per-shard locks
Hierarchical:   L1 → L2 → DB; populate up; invalidate down via Pub/Sub
```
