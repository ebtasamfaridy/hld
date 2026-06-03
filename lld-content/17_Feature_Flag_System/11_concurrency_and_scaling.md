# 11 · Feature Flag System — Concurrency & Scaling

## SDK side

### The hot path is read-only-ish

The SDK's `FlagStore` is read 20 M times/sec across a fleet. Writes happen only on flag updates from the server (~50/sec across whole company → per SDK far less).

Reads must be lock-free; writes can take a tiny lock.

Implementation:
- `ConcurrentHashMap<String, Flag>`. Reads are lock-free.
- A whole-flag replacement on update — the new `Flag` object is built off-thread, then `map.put(key, newFlag)` is atomic.
- The `Flag` itself is **immutable** (records / final fields). Multiple evaluators can read concurrently without locks.

### Bucketing must be pure
`hash(flag.key + ":" + userId) mod 10000`. No mutation, no side effects. Same input → same output forever. This is the single most important contract in the system.

### Atomic apply across multiple flags
Sometimes a coordinated change touches multiple flags (e.g., enabling a new module flips 3 flags). The SDK should apply them atomically.

Two options:
1. **Per-flag atomic puts** — most flags are independent; consistent enough.
2. **Snapshot pointer** — entire `FlagStore` swap. The store is `volatile FlagSnapshot snapshot;`; write replaces it; readers always see one consistent snapshot.

LaunchDarkly takes (1); strict-correctness systems take (2). The interview answer: "use snapshot if there are coordinated multi-flag changes."

## Server side

### Optimistic concurrency for admin writes
- `If-Match: version` header.
- DB `UPDATE … WHERE version = $expected RETURNING ...`.
- 0 rows updated → 409.

This handles concurrent admin edits cleanly without locking.

### Update-bus partitioning
Kafka topic `flag.updates` partitioned by `(envId, flagKey)`. Same flag's updates always go to the same partition → consumer sees them in order. SDKs apply only newer versions (compare version numbers) so out-of-order delivery is also handled.

### Edge SSE fanout

```
Bus consumer → Edge node holds N SSE clients
           → broadcast received update to each
```

A single Edge node holds 50 K SSE connections (long-lived; ~10 KB memory each → 500 MB). Run 10 Edge nodes for 500 K SDKs.

Sticky routing of SDKs to Edge nodes via consistent hashing on tenant id keeps the same SDK on the same node across reconnects.

### Reconnect storms
Deploys cause many SDKs to restart simultaneously and request bootstrap. Mitigations:
- CDN-cached snapshot — origin never sees the storm.
- Random jitter on SDK startup before opening SSE (`sleep(rand(0, 5s))`).
- Edge nodes apply per-tenant connection rate limits.

## Concurrency hot spots

| Hot spot | Mitigation |
| --- | --- |
| 20M reads/sec on FlagStore | Lock-free ConcurrentHashMap; immutable Flag |
| 50 admin writes/sec on Postgres | Trivial; single primary handles |
| 500 K SSE clients | Edge fanout; consistent hashing for sticky routing |
| Bus fanout 50 → 500K | Two-tier: Kafka → Edge → SDK |
| Bootstrap on deploy | CDN snapshot + jitter |

## Scaling

| Knob | Scale axis |
| --- | --- |
| More Edge nodes | More SSE connections |
| More CDN locations | Faster bootstrap globally |
| Postgres replica reads | Admin UI reads (writes still primary) |
| Snapshot frequency | Freshness of CDN bootstrap |
| Bus partition count | Higher write throughput |

## Failure modes

| Failure | Behavior |
| --- | --- |
| SDK has stale config | Still functional; eventual update |
| SDK can't reach Edge | Fall back to polling |
| SDK can't reach polling | Use cached snapshot from disk; if none, defaults |
| Edge node crashes | SDKs reconnect to a different node via consistent hashing |
| Bus down | Admin writes still succeed (DB+audit); updates queue locally; pushed when bus recovers |
| DB down | Admin writes fail; SDKs unaffected (still using cached config) |
| Slow operator (huge regex on 1M users) | Pre-validate at write time; reject regexes that fail timeout |

## Anti-cheat

- Strict validation at admin API write time.
- Cycles in prerequisite graph rejected.
- Variation IDs validated to exist.
- RBAC: production flips require approval; dev flags can be flipped by any developer.

## Output

```
SDK side:    lock-free reads on ConcurrentHashMap; immutable Flag objects;
             atomic per-flag put or snapshot pointer for coordinated changes
Server side: optimistic CAS on admin writes; Kafka partitioned by env+key
Fan-out:     Kafka → Edge nodes (50K SSE each) → SDKs; consistent hashing for stickiness
Resilience:  CDN snapshots for bootstrap storms; polling fallback when SSE drops;
             SDKs always functional with cached config
```
