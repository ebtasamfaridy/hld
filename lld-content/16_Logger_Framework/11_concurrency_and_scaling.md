# 11 · Logger Framework — Concurrency & Scaling

## The fundamental problem

Loggers are called from every thread, on every request. The hot path **must** be:
1. Lock-free.
2. Allocation-free when level is disabled.
3. Bounded latency even when the disk is slow.

## Concurrent reads of `LoggerContext`

`getLogger(name)` is called once per `Logger` instance per class — usually a static field initialized once. Even so, the registry is `ConcurrentHashMap<String, Logger>`. `computeIfAbsent` ensures one create per name even under racing initialization.

## Logger field publication

```java
public final class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    ...
}
```

Static-init happens once; the field is `final`; safe publication is automatic.

## Effective level cache (volatile read)

The hot path is:
```
if (level.rank() >= effectiveLevelCached) { ... }
```
`effectiveLevelCached` is a `volatile int`. Read is a single atomic load; no lock.

When config reloads:
```
ctx.config = newConfig;            // volatile write
for (logger in registry) {
    logger.effectiveLevelCached = recompute(logger);   // volatile write
}
```

Loggers calling `isEnabled` during the swap may see old or new values; both are correct (no torn state).

## Appender list: copy-on-write

Each logger's `appenders` is a `CopyOnWriteArrayList` or an immutable list rebuilt on config change. Iteration is lock-free; updates are rare (only at config reload).

## LogEvent: immutable, safely shared

Built once on the calling thread. Multiple appenders read the same instance. No data race because no field is mutable.

The only nuance: `args` is an `Object[]`. The args themselves may be mutable. The convention: don't mutate after logging. Real Log4j2 lets you opt into "immutable args" mode where the framework defensively copies.

## MDC: per-thread

`ThreadLocal<Map<String,String>>`. No contention. The snapshot taken by `LogEvent` is an immutable copy — async appenders see the values at log time, not the now-changed thread state.

## Async ring buffer

Producers (the calling threads) and the single consumer (drain thread) coordinate via:
- `LinkedBlockingQueue` — simple; offers backpressure or non-blocking offer.
- LMAX Disruptor — sequences + cache-line padding; ~10× faster.

For our skeleton, `ArrayBlockingQueue` is fine. Production: Disruptor.

## File appender concurrency

Multiple threads writing the same file? Two designs:
1. **Single-writer**: only the appender thread writes; producers enqueue. Async wrapper does this implicitly.
2. **Lock per file**: synchronized append. Slower under contention.

Both work. Async wrapper is the production answer.

## Backpressure policy

When the async queue is full:
| Policy | Behavior | Use case |
| --- | --- | --- |
| BLOCK | Producer waits | Lossless; OK when bursts are short |
| DROP_NEWEST | New event discarded | Lossy; throughput-critical |
| DROP_OLDEST | Evict head; insert new | Lossy; preserve recency |
| LOG_TO_DEAD_LETTER | Spill to a separate file | Lossy at primary; recoverable |

Pick based on importance: ERROR logs → BLOCK; DEBUG logs → DROP.

## Log sampling (V2)

Some logs fire millions of times for the same situation. Sampling drops K-1 of every K to reduce volume:
- **Random**: `if (random < 1/K) log else skip`.
- **Rate-limited**: token bucket per logger; max N events/sec.
- **Conditional**: sample DEBUG but always pass WARN+.

## Memory footprint

A logger object is tiny (~200 B). Even 10 K loggers in a large app is 2 MB — negligible.

`LogEvent` is per-call but lifetime is short; in async mode the ring buffer holds at most queueSize events at once.

## Recursive logging

Imagine an appender that, on error, calls `log.error(...)` — which goes back through the same appender → infinite recursion → stack overflow.

Mitigation: a `ThreadLocal<Boolean> reentrant` guard. On entry, check; if already in, route to the internal status logger instead.

## Failure modes

| Failure | Behavior |
| --- | --- |
| Disk full | IOException caught; log via status; appender enters ERROR; resumes when next append succeeds |
| Drain thread crashes | Watcher restarts it; events buffered while dead are lost |
| Async queue full | per backpressure policy |
| Layout NPE on bad event | Caught; safe-string substituted |
| OOM during message format | Truncate at configured max-message-size |
| Recursive log | Reentrant guard + status logger |

## Output

```
Hot path:   volatile read of effective level → return if disabled (~5ns)
Concurrency: ConcurrentHashMap registry + CoW appender lists + immutable LogEvent
MDC:        ThreadLocal; snapshot at event creation
Async:      single drain thread; ring buffer; backpressure policy per appender
File:       single-writer via async wrapper
Failure:    swallow at append time; status logger; never crash app
```
