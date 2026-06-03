# 03 · LRU Cache — High-Level Design

## In-process cache

```mermaid
flowchart LR
    App[Application] -- get/put --> Cache[Cache]
    Cache -- on miss --> Loader["Loader (DB / RPC)"]
    Cache -- evict --> Listener[EvictionListener]
    Cache --- Stats[Stats]
    Cache --- Policy[EvictionPolicy<br/>LRU / LFU]
    Cache --- TTL[TtlIndex]
```

## Distributed cache hierarchy

```mermaid
flowchart TB
    App1[App1] --> L1A[L1 Caffeine]
    App2[App2] --> L1B[L1 Caffeine]
    L1A -- L1 miss --> L2[L2 Redis]
    L1B -- L1 miss --> L2
    L2 -- L2 miss --> DB[(Source of truth: DB)]
    DB -- write --> Inv[Invalidation:<br/>publish key→Redis;<br/>L1 listens]
    Inv -.-> L1A
    Inv -.-> L1B
```

L1 (in-process) is the fastest layer: <1 µs hits.
L2 (Redis) is shared, cross-process: ~100 µs hits.
DB is the truth: 1–10 ms.

A miss at L1 falls to L2; miss at L2 falls to DB; the value is populated up the chain.

## Cache types

| Type | Use case |
| --- | --- |
| Local (in-process) | Hot path, no cross-process consistency required |
| Distributed (Redis) | Cross-app coherency, large capacity |
| Hierarchical (L1 + L2) | Best of both: speed + coherency |

## Internal structure (in-process LRU)

```
+--------------------------+
| HashMap<K, Node>         |
|   K1 → ●─────────┐       |
|   K2 → ●─────┐   |       |
|   K3 → ●─┐   |   |       |
+----------|---|---|-------+
           |   |   |
   tail ←  ●─→ ● ←→ ● ←→ ● → head
   (LRU)               (MRU)
```

- Map points to nodes in the doubly linked list.
- `get`: lookup in map (O(1)); move node to head (O(1) via prev/next pointers).
- `put`: insert or update; move to head; if size > capacity, evict tail.

## Hot operations

### get(key) hit

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cache
    participant Map
    participant DLL

    App->>Cache: get(K)
    Cache->>Map: lookup K
    Map-->>Cache: Node
    Cache->>DLL: moveToHead(Node)
    Cache-->>App: value
    Cache->>Cache: stats.hit++
```

### get(key) miss with loader

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cache
    participant Lock as KeyLock
    participant Loader

    App->>Cache: getOrLoad(K, loader)
    Cache->>Cache: miss
    Cache->>Lock: acquire(K)
    Cache->>Cache: re-check (another thread may have loaded)
    alt still missing
      Cache->>Loader: load(K)
      Loader-->>Cache: value
      Cache->>Cache: put(K, value)
    end
    Cache->>Lock: release(K)
    Cache-->>App: value
```

The "re-check after acquiring lock" pattern (double-checked locking on lookup) avoids redundant loads.

### put(key, value)

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
      Cache->>DLL: update value, moveToHead
    else new
      Cache->>DLL: addToHead(K, V)
      Cache->>Map: put K → Node
      alt over capacity
        Cache->>DLL: removeTail() → evicted
        Cache->>Map: remove evicted.K
        Cache->>Listener: onEvict(K, V)
      end
    end
```

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Loader throws | Propagate; don't cache exception (or cache briefly: "negative TTL") |
| Listener throws | Catch and log; don't break cache state |
| OOM during put | Trigger emergency eviction; reduce capacity dynamically |
| Concurrent eviction during read | Atomic via stripe lock or CAS |
| Distributed L2 down | Fall through to DB; degrade gracefully |

## Output

```
Layout:    HashMap<K, Node> + DoublyLinkedList for LRU order
Hierarchy: L1 in-process + L2 Redis + DB; populate up on miss
Get:       lookup + moveToHead, O(1)
Put:       insert/update + moveToHead + evict-if-over-cap, O(1)
Loader:    single-flight (per-key lock + double-check)
```
