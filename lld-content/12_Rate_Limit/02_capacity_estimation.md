# 02 · Rate Limiter — Capacity Estimation

## Single-tenant API gateway

```
Requests:           50 K RPS sustained, 200 K peak
Per request:        ~3 limiter checks (IP, user, route)
Limiter checks/sec: 150 K sustained, 600 K peak
Latency budget:     < 1 ms (limiter must be invisible to user)
```

## Distributed Redis cluster

```
Unique keys / minute:    1 M
State per key:           ~64 B (Token Bucket: tokens + last-refill ts)
Hot working set:         ~64 MB
Redis nodes:             3 (1 primary + 2 replicas) — fits comfortably in RAM
Network ops:             1 round-trip per check (Lua script atomic)
```

## In-process limiter (per-pod)

For a pod doing 50 K req/s with 3 checks each:
```
ConcurrentHashMap entries: 1 M (IP-keyed)
Memory:                    ~200 MB
GC pressure:               watch out — use long-keyed structures
```

## What forces the design

1. **Latency** — every request hits the limiter. Sub-millisecond is mandatory.
2. **Atomicity** — distributed accuracy comes only from atomic ops at the store layer.
3. **Memory** — per-key state must be < 100 B. Token Bucket: 16 B (long ts + long tokens). Sliding log: O(window × rps) — expensive.
4. **Failure isolation** — Redis outage cannot take down the API.

## Hot path

```
Request →
  KeyExtractor → 3 keys (IP, user, route)
  for each key:
    LuaScript on Redis (CHECK_AND_DECREMENT) → allow/deny + remaining
  if any deny: return 429 with Retry-After
```

End-to-end limiter overhead: ≤ 1 ms (1 round-trip Redis + decision).

## Output

```
API gateway:  50 K RPS × 3 checks = 150 K Redis ops/sec
Redis cluster: 3 nodes; ~64 MB hot working set; 1 RT per check
Latency:      < 1 ms p99 with Lua atomic
Memory:       ~200 MB in-process for 1 M IPs
```
