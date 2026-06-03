# 05 · LRU Cache — Storage / Persistence

The LRU cache itself is **memory-resident**. There is no DB schema. But the surrounding system has interesting persistence concerns.

## In-process state — no persistence

By default, the cache vanishes on process restart. This is desirable: the cache is meant to be regenerated.

Some applications want **persistence on shutdown** for warm-start. Approach:
- On shutdown hook, serialize the live entries to disk.
- On startup, deserialize and re-populate.
- Bound size to avoid huge dumps; consider writing to a memory-mapped file or an embedded KV store (LMDB, RocksDB).

## Distributed L2 (Redis)

When using Redis as a shared L2 cache:

```
Key:   cache:{namespace}:{entityType}:{id}
Value: serialized JSON or MessagePack
TTL:   set explicitly
Eviction policy (Redis-side): allkeys-lru
```

Example:
```
SET cache:catalog:movie:m-123 '{"id":"m-123","title":"Tenet",...}' EX 600
GET cache:catalog:movie:m-123
DEL cache:catalog:movie:m-123  (on update)
```

## Source-of-truth schema (Postgres example, illustrative)

```sql
CREATE TABLE catalog (
  id          uuid PRIMARY KEY,
  payload     jsonb NOT NULL,
  version     bigint NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);
```

The `version` column drives invalidation:
1. Update the row → `version` increments via trigger.
2. Application reads can include a small `version_index` table or a Redis key `version:{entity}` that drives cache validity.

## Cache-key design

| Pattern | When |
| --- | --- |
| `cache:{type}:{id}` | Direct entity cache |
| `cache:{type}:{id}:v{ver}` | Versioned: write a new key on update; old key TTLs out |
| `cache:list:{query-hash}` | Result-set caching |
| `lock:{key}` | Single-flight loader lock |

Versioned keys are particularly nice: writers don't have to invalidate; old keys age out via TTL while new keys are populated.

## Invalidation strategies (most important section)

### 1. TTL-only
Simple. Stale data tolerated for up to TTL seconds. Best for "reasonably fresh" data.

### 2. TTL + explicit invalidation
On a write to the source of truth, send a `DEL cache:{key}` to Redis. Pub/Sub propagates to all L1 caches: each subscribes to a channel and on message, removes the local entry.

### 3. Versioned keys
Update bumps version; cache key encodes version. Old keys naturally TTL out. Reads always fetch the latest.

### 4. Write-through
Application writes to cache and DB in the same operation. Cache always consistent (within the write path). But DB and cache can drift if external writes bypass the application.

### 5. Read-through with refresh-ahead
Cache reloads expired entries proactively before they're requested. Uses a timer per entry.

| Strategy | Consistency | Complexity |
| --- | --- | --- |
| TTL only | eventual | trivial |
| TTL + invalidate | near-strong | moderate (Pub/Sub fanout) |
| Versioned keys | strong (atomic version bump) | low–moderate |
| Write-through | strong on write path | bypass risk |
| Refresh-ahead | reduces miss latency | more loads |

## Output

```
In-process:    no persistence (default); optional serialize-on-shutdown
Redis L2:      cache:{type}:{id}[:v{ver}], with TTL; allkeys-lru server-side
Invalidation: TTL only / TTL + DEL / versioned keys / write-through / refresh-ahead
Truth:         DB rows with version column drives cache key naming
```
