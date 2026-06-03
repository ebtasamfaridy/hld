# 11 · Rate Limiter — Concurrency & Scaling

## Concurrency hotspots

### 1. Two requests for the same key in the same millisecond

The race: read state → compute → write state. Without atomicity, both read the same `tokens=1`, both decrement, both allow.

**Mitigation**:
- In-memory: `ConcurrentHashMap.compute(key, ...)` is atomic per key. The lambda runs under the bin lock.
- Redis: Lua script runs atomically.

### 2. Cross-pod contention

In-process limiters maintain per-pod state. A caller hitting M pods round-robin sees ≈ M× the limit.

**Mitigation**:
- Use Redis-backed limiter for global limits.
- Or use sticky-routing for the most restrictive scope (e.g., per-API-key sticks to one pod).
- Or accept the inflation (often fine for IP-level coarse limits).

### 3. Hot key (one user blasting requests)

A single key handles 10 K req/s. The Redis primary serializes all those Lua calls on that key.

**Mitigation**:
- Local pre-aggregation: count locally for ε ms, then push to Redis as a batch. Trade slight inaccuracy for huge throughput gain.
- **Tiered limit**: a fast-path local limiter at burst-1 RPS rejects most; slow-path Redis only on borderline.

### 4. Clock skew

Different pods have different `System.currentTimeMillis()`. If used as `now` in the algorithm, refill calculations diverge.

**Mitigation**:
- Use Redis server time inside Lua (`redis.call('TIME')`).
- Or NTP-synced clocks (acceptable for ±10 ms).

### 5. Configuration hot-reload

Operator changes `1000/min → 500/min` mid-day. Existing keys have state from the old config (`tokens=900`).

**Mitigation**:
- Token Bucket: clamp `tokens = min(tokens, newCapacity)`. Caller's tokens may be stripped immediately, which is the *desired* behavior.
- Sliding/fixed: the count carries forward; new limit takes effect at next check.

## Scaling

### Vertical (one Redis primary)

Token Bucket Lua at ~50 K ops/sec on a single Redis primary. For higher RPS, shard.

### Sharding by key

```
shard = hashSlot(key) % numShards
```

Redis Cluster does this natively. Algorithm scripts must use single-key access (Lua scripts in Redis Cluster require all KEYS to be on the same shard — which they are by definition for our single-key algorithms).

### Multi-key Lua (V2)

For two-phase quote-then-deduct across scopes, all keys must be on the same shard. Use **hash tags**:

```
{userid}:limit:user      → all keys with same {userid} hash to same shard
{userid}:limit:route
```

Then a single Lua script can atomically check both.

## Latency

| Path | Target |
| --- | --- |
| In-memory Lua-equivalent | < 100 µs |
| Redis Lua single-key | < 1 ms |
| Cross-region Redis | NOT recommended for hot path |

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Redis primary failover | Fail-open during the gap; resume after election |
| Redis script not found | Fallback to EVAL with full script body |
| Network partition | Circuit-breaker → fail-open |
| OOM on Redis | TTL eviction; LFU policy; alerts |
| Lua bug | Versioned scripts; test fixtures |

## Backpressure

If the limiter itself becomes slow (Redis slowness), the **caller's request latency** goes up. Mitigation: the limiter has its own internal timeout (e.g., 5 ms) and falls back to Allow on timeout.

## Capacity numbers

```
1 Redis node:        50 K ops/s for Token Bucket Lua
3-node cluster:     150 K ops/s
6-node cluster:     300 K ops/s

Memory per 1 M keys: ~80 MB
```

A 16 GB Redis cluster handles 100 M unique keys/day.

## Output

```
Concurrency:    Lua atomicity in Redis; ConcurrentHashMap.compute in-memory
Hot keys:       local pre-aggregation; tiered limit
Sharding:       hash-slot in Redis cluster; hash tags for multi-key
Latency:        < 1 ms p99 for Redis; < 100µs in-memory
Failure:        fail-open via circuit breaker; alert on degraded mode
```
