# 11 · Circuit Breaker — Concurrency & Scaling

## The hot path is "decide allow/reject"

This runs millions of times/sec. Must be:
1. Lock-free.
2. Allocation-free.
3. Branch-prediction-friendly (CLOSED is the common case; the JIT optimizes for it).

```java
public AcquireResult acquire() {
    State s = stateRef.get();          // volatile read
    return switch (s) {
        case CLOSED -> AcquireResult.ALLOWED;
        case OPEN -> {
            if (System.nanoTime() < openUntilNanos) {
                yield AcquireResult.REJECTED;
            }
            // expired; transition to HALF_OPEN if we win the CAS
            if (stateRef.compareAndSet(OPEN, HALF_OPEN)) {
                halfOpenPermits.release(config.permittedCallsInHalfOpen);
            }
            yield halfOpenPermits.tryAcquire()
                ? AcquireResult.ALLOWED
                : AcquireResult.REJECTED;
        }
        case HALF_OPEN -> halfOpenPermits.tryAcquire()
                ? AcquireResult.ALLOWED
                : AcquireResult.REJECTED;
        case FORCED_OPEN -> AcquireResult.REJECTED;
        case FORCED_CLOSED, DISABLED -> AcquireResult.ALLOWED;
    };
}
```

Volatile reads + CAS for transitions + Semaphore for HALF_OPEN. No `synchronized`.

## Recording outcomes

```java
public void onSuccess(long durationNs) {
    boolean slow = durationNs >= config.slowCallThresholdNs;
    if (slow) window.recordSlow(); else window.recordSuccess();

    State s = stateRef.get();
    if (s == HALF_OPEN) {
        if (halfOpenSuccess.incrementAndGet() >= config.permittedCallsInHalfOpen) {
            transitionToClosed();
        }
    } else if (s == CLOSED) {
        Metrics m = window.metrics();
        if (m.calls() >= config.minimumCalls
            && (m.failureRate() >= config.failureRateThreshold
             || m.slowCallRate() >= config.slowCallRateThreshold)) {
            transitionToOpen();
        }
    }
}
```

The `metrics()` snapshot is computed inline. For a count-based ring buffer this is `O(N)`; for time-based it's `O(numBuckets)`. With N=100 or 60 buckets, this is microseconds. Acceptable on the slow path.

To make it cheaper, cache the metrics and refresh every M calls or every K ms.

## CAS race for transitions

Two threads may simultaneously notice "failure rate exceeded." Only one CAS succeeds:

```java
private void transitionToOpen() {
    if (stateRef.compareAndSet(CLOSED, OPEN) || stateRef.compareAndSet(HALF_OPEN, OPEN)) {
        openUntilNanos = System.nanoTime() + config.waitDurationNanos;
        publish(StateChange.toOpen());
    }
}
```

Threads losing the CAS observe `OPEN` already set; their attempt is a no-op.

## Sliding window concurrency

### Count-based ring buffer
- `AtomicInteger nextIndex` for slot allocation.
- `AtomicReferenceArray<Outcome>` for slots.
- Read-side computes counts via `O(N)` scan. Acceptable; happens occasionally.

### Time-based bucketed
- N buckets. `LongAdder` per outcome per bucket: `successes`, `failures`, `slow`.
- Bucket index = `(nanoTime() / bucketSizeNanos) mod N`.
- Bucket reset: when index advances, lazily zero the old bucket on first write.

`LongAdder` shards updates across cells; gives much better throughput than `AtomicLong` under high contention.

## HALF_OPEN permits

A `Semaphore` with permits = `permittedCallsInHalfOpen`. `tryAcquire()` is fast and lock-free for non-fair semaphores. Permits are released on transition (back to N when entering HALF_OPEN; drained on exit).

## Listener concurrency

`CopyOnWriteArrayList<EventListener>`. Concurrent iteration during state changes; rare add/remove. Listeners must not throw (caught by breaker).

## Bulkhead

```java
public final class Bulkhead {
    private final Semaphore sem;
    private final long acquireTimeoutMs;

    public boolean tryAcquire() throws InterruptedException {
        return sem.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
    }
    public void release() { sem.release(); }
}
```

Caller acquires before the breaker. Limits concurrent calls regardless of breaker state.

## Hot bottlenecks

| Bottleneck | Mitigation |
| --- | --- |
| 100 threads simultaneously trip CAS | One wins; others see new state — no harm |
| Window read on every call | Read only periodically; otherwise rely on counters |
| Lock contention on listeners | CopyOnWriteArrayList for rare-write |
| Semaphore on every CLOSED call | Don't! semaphore only for HALF_OPEN/Bulkhead |

## Failure modes (of the breaker itself)

| Failure | Behavior |
| --- | --- |
| Listener throws | Caught; logged; breaker continues |
| Probe in HALF_OPEN hangs forever | Outer Timeout decorator handles it |
| Manual force-open during normal operation | Transition immediately; ignore signal until reset |
| Counter overflow on long-running CLOSED | Reset counters on state changes; periodically refresh |
| Clock anomaly | `System.nanoTime()` is monotonic |

## Distributed mode

Per-instance breakers are usually fine. If you really need shared state:
- Eventual consistency: instances push counters to Redis; coordinator computes; pushes back.
- Cost: extra hop on the hot path; defeats < 1 µs.
- When to do it: when downstream is geographically partitioned (only some instances see failures).

For most cases, **don't** distribute — failures are correlated; per-instance is fine.

## Output

```
Hot path:    volatile state read + (CAS for transitions); ~10ns CLOSED
Window:      ring buffer or bucketed time window; LongAdder for low contention
HALF_OPEN:   non-fair Semaphore for probe permits
Listeners:   CopyOnWriteArrayList; listener errors swallowed
Distributed: rarely needed; per-instance is the default answer
```
