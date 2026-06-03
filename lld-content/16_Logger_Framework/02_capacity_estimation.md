# 02 · Logger Framework — Capacity Estimation

## Per-process load

```
Hot service:               50 K req/s
Average log lines/req:     5 (request, validations, response, etc.)
Total log events/sec:      250 K
Average event bytes:       300 B (formatted)
Bytes/sec:                 75 MB/s ≈ 6.5 TB/day
```

That's per-process. Big.

## Why this drives async

A 75 MB/s write to a single file is bounded by:
- Disk write speed (HDD: 100 MB/s; SSD: 500 MB/s).
- `fsync` rate (hundreds per second, far below 250 K events/s).
- Lock contention on the file descriptor.

If we do **synchronous** logging on the request path:
- Each log call adds a few microseconds (formatting + write).
- 5 calls × 5 µs = 25 µs of latency tax per request.
- Under load with disk congestion, p99 can spike to milliseconds.

If we do **async** logging:
- Caller pays only the enqueue cost (~100 ns).
- A background drain thread does the formatting and writing.
- A burst of 100 K events buffers cleanly; sustained rate hits the disk speed limit.

## Disk math

```
6.5 TB/day per host. Compressed gzip → ~1 TB/day. Retention 7 days = 7 TB.
With log rotation (gzip after roll), local disk use stays bounded.
Ship to central system → S3 storage = ~$50/month per host of logs.
```

## Disabled levels are the silent cost

```
log.debug("user data: " + buildHugeString(user))
                         └─────────────────────┘
                               This runs even if DEBUG is disabled.
                               Concatenation = string allocations.
```

If `buildHugeString` is 10 µs and called 50 K req/s with 5 debug calls/req → 2.5 seconds of CPU/sec spent building strings nobody sees. **That's a whole core.**

The fix: parameterized API. `log.debug("user data: {}", user)` defers the `toString` until the level is verified enabled. This is **the** reason SLF4J exists.

## Thread contention

```
100 threads × 5 logs/req × 50 K req/s ≈ 25 M log calls/sec
If each log goes through a synchronized Appender:
  25 M ops/sec on one lock = the lock is the system.
```

Mitigations:
- **Async wrapper** moves contention to a lock-free ring buffer.
- **Per-thread MDC** avoids shared maps.
- **Lock-free or per-thread buffering** at the appender (Log4j2 + Disruptor does this).

## What forces design

1. **Parameterized API + level pre-check** — eliminates allocation on disabled paths.
2. **Async with ring buffer** — removes I/O from the request path.
3. **Hierarchy with cached effective level** — single integer compare to skip a log call.
4. **Pluggable appenders** — fan-out without rewriting callers.
5. **Configuration model** — declarative, hot-reloadable; ops change levels without redeploy.

## Output

```
Throughput:   250K events/sec/host; 75 MB/s
Disk:         6.5 TB/day → bounded via rotation + offload to central
Hot path:     <100ns per log call (async); <5ns for disabled levels
Driver:       async + lazy formatting + cached effective level
```
