# 05 · Rate Limiter — Storage Design

The "DB" for a rate limiter is the **state store** for per-key counters. Two real options.

## Option A — In-process map

```java
ConcurrentHashMap<String, BucketState>
```

For Token Bucket, `BucketState = { double tokens, long lastRefillNanos }` ≈ 16 bytes.

- Microsecond latency.
- Per-pod state (not shared).
- TTL via a periodic eviction sweep.

## Option B — Redis (production default for distributed)

Each key stored as a Redis Hash:

```
HKEY: rl:tb:{family}:{value}        e.g., "rl:tb:user:42"
HFIELDS:
  t:  current tokens (float)
  ts: last refill timestamp (ms epoch)
EXPIRE: 3600 s
```

The Lua script atomically reads, computes, writes. One round-trip per check.

For Sliding Window Counter:

```
HKEY: rl:swc:{family}:{value}
HFIELDS:
  cw_start: ts of current window start
  cw_count: int
  pw_count: int
EXPIRE: 2 × W
```

For Sliding Window Log:

```
ZSET: rl:slog:{family}:{value}
SCORE = ts ms; MEMBER = ts ms
ZREMRANGEBYSCORE 0 (now-W) — drop expired
ZADD now now — add current
ZCARD — current count
EXPIRE = W
```

## Key naming convention

```
rl:{algo}:{family}:{value}
   |     |        |
   |     |        +-- the actual id (IP, user id, route)
   |     +----------- key family for sharding/visibility
   +----------------- algorithm tag (so multiple algos coexist on same Redis)
```

## Memory estimation

Redis Hash overhead ≈ 50 B per key + a few bytes per field. Total ~80 B per Token Bucket key.
1 M keys → ~80 MB. Fits comfortably in a 16 GB Redis node.

## Eviction policy

- TTL on every key (1 hour for Token Bucket).
- `maxmemory-policy: allkeys-lfu` to evict cold keys if memory pressure.
- LFU > LRU here because cold keys are likely benign IPs that won't return; we want to keep recent active ones.

## Persistence

We don't persist limiter state across restarts; on restart, every key starts at "full bucket." This is intentional — losing state is fine; the worst case is a brief permissive window.

## Audit log (separate DB)

For abuse investigation: log every `Deny` event:

```sql
CREATE TABLE rate_limit_denials (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    key_family TEXT NOT NULL,
    key_value TEXT NOT NULL,
    scope TEXT NOT NULL,
    request_id TEXT,
    user_agent TEXT
) PARTITION BY RANGE (ts);

CREATE INDEX idx_rl_denials_user ON rate_limit_denials (key_family, key_value);
```

Async write — never block the request path.

## Output

```
In-process:    ConcurrentHashMap, ~16 B per key
Redis:         Hash per key, ~80 B per key, Lua script atomic
Eviction:      TTL + LFU
Persistence:   none for state; audit denials async to Postgres
```
