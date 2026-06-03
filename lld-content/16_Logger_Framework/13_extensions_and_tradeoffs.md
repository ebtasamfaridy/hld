# 13 · Logger Framework — Extensions & Tradeoffs

## Extensions

### 1. Rolling file appender
Size + time + compression + retention policies.

### 2. Network appenders — Syslog / HTTP / Kafka
Same `Appender` interface; new sinks. Use the async wrapper to absorb network jitter.

### 3. JSON / structured logging
JSON layout already implemented. Production benefits: Splunk / ELK / Loki ingest cleanly without parsing patterns.

### 4. Log sampling
Token-bucket per logger. Drops uniformly at high rate. Configurable per-level.

### 5. LMAX Disruptor
Replace the `BlockingQueue` with a Disruptor ring buffer. ~10× faster on the producer side.

### 6. Hot-reload via WatchService
File watcher + atomic config swap. Ops can change levels without restart.

### 7. Bridge to / from SLF4J
Most apps use SLF4J as the API. We can plug in either as an SLF4J-compatible binding (so apps using SLF4J use our framework) or as a pass-through (we delegate to SLF4J). This is what Logback / Log4j2 do.

### 8. Markers
Tag log events with logical markers (e.g., `AUDIT`, `SECURITY`). Filters and layouts can branch on markers without parsing message text.

### 9. Audit appender
Hash-chained for tamper detection.

### 10. OpenTelemetry integration
Auto-stamp every log event with the current trace/span ID from OTel context.

## Tradeoffs

### Sync vs async

| Sync | Async |
| --- | --- |
| Lossless on JVM crash | Best-effort drain on shutdown |
| Latency = I/O latency | Latency ≈ enqueue cost (~100 ns) |
| Easier debugging (logs visible immediately) | Slight delay; great under load |
| **Pick**: async for production hot paths; sync for critical ERROR/FATAL |

### Allocation: build LogEvent eagerly vs lazy?

We build `LogEvent` only after the level check. The level check itself is one volatile read + integer compare. No allocation if disabled.

For very hot disabled-level paths, even the `Object[]` for varargs allocates! Mitigate with overloads (`info(String)`, `info(String, Object)`, `info(String, Object, Object)`) — the JIT can elide the array for fixed-arity calls. SLF4J does this.

### Pattern layout flexibility vs cost

| Pattern | JSON |
| --- | --- |
| Human-readable | Machine-parseable |
| Faster to format (string concat) | Slower (escaping, structure) |
| Aggregator must parse | Aggregator ingests structured |
| **Pick**: JSON in production where logs ship to a system; pattern in dev |

### Drop policy

| Policy | Use case |
| --- | --- |
| BLOCK | ERROR/FATAL — never lose |
| DROP_NEWEST | DEBUG/TRACE — lossy is fine |
| DROP_OLDEST | Operational stream where recency matters |

Real Logback / Log4j2 default to DROP for async wrapper. Configure per-appender.

### Hierarchy strictness

We treat dot-separation as parent-child. What if someone uses "MyService" (no dots)? Its parent is root. What if they use "Foo.Bar" (Pascal)? Same rules. The convention is FQN-style; the framework doesn't enforce it but works best when followed.

### Per-thread MDC vs explicit context

| MDC | Explicit |
| --- | --- |
| Implicit; works with any code | Fully traceable; no surprise |
| `ThreadLocal`-bound — needs care across thread pools | No surprises |
| **Pick**: MDC for request context; reset on thread reuse (Web frameworks already do this) |

A subtle gotcha: when work is offloaded to an `ExecutorService`, the MDC of the calling thread is **not** transferred to the worker thread. SLF4J / Logback have helpers to capture and restore MDC across thread boundaries.

## Open questions

- Synchronous `fsync` on each line? (No; flush every N events or N ms.)
- Should we ever call `System.exit` on a fatal logging error? (No.)
- Should we support log compression in-process? (No; do it post-roll.)
- How big should the async queue be? (Start 8 K; tune based on burst pattern.)

## Output

```
Extensions:    rolling files, network appenders, JSON, sampling, Disruptor,
               hot-reload, SLF4J bridge, markers, audit chain, OTel
Tradeoffs:     sync vs async; pattern vs JSON; drop vs block; hierarchy laxity
Pre-decided:   parameterized lazy formatting; ConcurrentHashMap registry;
               volatile effective level; LogEvent immutable; reentrant guard;
               async opt-in via decorator
Open Qs:       fsync policy, queue size, MDC propagation across pools
```
