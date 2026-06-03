# 09 · LRU Cache — State Machines

## Entry lifecycle

```mermaid
stateDiagram-v2
    [*] --> ABSENT
    ABSENT --> RESIDENT : put / loader fills
    RESIDENT --> RESIDENT : get (moveToHead) or put (update)
    RESIDENT --> ABSENT  : remove / evict (SIZE) / evict (EXPIRED)
    ABSENT --> [*]
```

States are deliberately simple; the cache is a high-throughput data plane.

## Loader state per key (single-flight)

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> LOADING : first thread begins loader
    LOADING --> IDLE : loader completes; value cached
    LOADING --> FAILED : loader throws
    FAILED --> IDLE : caller observes; lock released
```

In practice the "lock" is an entry in a `ConcurrentHashMap<K, Object>` or a `compute()` lambda; the FSM is implicit.

## TTL sweeper state

```mermaid
stateDiagram-v2
    [*] --> SLEEPING
    SLEEPING --> SWEEPING : timer fires
    SWEEPING --> SLEEPING : purge done
    SWEEPING --> SHUTTING_DOWN : cache.close()
    SHUTTING_DOWN --> [*]
```

## Cache builder state

```mermaid
stateDiagram-v2
    [*] --> CONFIGURING
    CONFIGURING --> CONFIGURING : capacity / ttl / policy / listener
    CONFIGURING --> BUILT : build()
    BUILT --> [*]
```

## Stats counters (informational, not really FSM)

```
hits, misses (LongAdder-backed)
evictions per cause: SIZE, EXPIRED, EXPLICIT, REPLACED
loadsSuccessful, loadsFailed
totalLoadTimeNs (for averages)
```

## Output

```
Entry:   ABSENT ↔ RESIDENT (move/update on get/put; evict on size/expired/explicit)
Loader:  IDLE → LOADING → IDLE | FAILED (single-flight invariant)
Sweeper: SLEEPING ↔ SWEEPING; SHUTTING_DOWN on close
```
