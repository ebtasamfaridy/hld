# 05 · Circuit Breaker — "Storage"

A circuit breaker is **in-process by default**. There is no traditional database; the storage problem is **how to record outcomes efficiently**.

## In-memory data structures

### Count-based window

A ring buffer of N slots:

```
type Slot = {
  outcome: SUCCESS | FAILURE | SLOW
  durationNanos: long
}

slots: AtomicReferenceArray<Slot>(N)
nextIndex: AtomicInteger
```

On record:
1. `i = nextIndex.getAndIncrement() % N`
2. `slots.set(i, slot)`

To compute failure rate:
- Linear scan; count failures + slow ÷ N.
- Cached snapshot updated periodically to amortize.

### Time-based window (bucketed)

Imagine 60 buckets, each covering 1 second:

```
type Bucket = {
  successes: LongAdder
  failures: LongAdder
  slowCalls: LongAdder
  startNanos: long
}

buckets: Bucket[60]
```

On record at `now`:
1. `bucketIdx = (now / 1_000_000_000) mod 60`.
2. If the bucket's `startNanos` is older than 60 s, reset it (volatile flip + zero counters).
3. Increment the appropriate `LongAdder`.

To compute failure rate over the last 60 s, sum across all buckets.

`LongAdder` is preferred over `AtomicLong` for highly-contended counters: it shards internally, returning the sum on read.

### Metrics snapshot

A `Metrics` value object built on demand from the window. Caching not necessary at this scale; snapshot computation is microseconds.

## State storage

```java
final AtomicReference<State> state = new AtomicReference<>(CLOSED);
volatile long openUntilNanos;          // when to auto-transition to HALF_OPEN
final Semaphore halfOpenPermits;
```

State transitions are CAS:
```java
if (state.compareAndSet(CLOSED, OPEN)) {
    openUntilNanos = nanoTime() + waitDurationNanos;
    halfOpenPermits.drainPermits(); // optional reset
    publish(StateChange.CLOSED_TO_OPEN);
}
```

CAS ensures exactly one transition under racing threads.

## Listener storage

`CopyOnWriteArrayList<Listener>` — concurrent iteration during state changes; rare adds.

## Distributed state (V2)

If multiple instances of a service want to share breaker state:

```
Redis hash:
  cb:{name}:state              "CLOSED" / "OPEN" / "HALF_OPEN"
  cb:{name}:openUntilEpochMs   long
  cb:{name}:counters           per-bucket counters incremented via HINCRBY
```

State reads cost a Redis round-trip per call — ~1 ms. Defeats the <1µs hot path.

Alternative: **eventual consistency**. Each instance has a local breaker; instances periodically (every 100 ms) push their counters to Redis; a coordinator computes global state and pushes back via pub/sub.

In practice, distributed breakers are uncommon. Per-instance breakers are good enough because failures are usually correlated (one downstream is down → all instances see failures).

## Persistence?

Breakers are typically **not** persisted across restarts. State after restart starts fresh as CLOSED. The first wave of traffic re-establishes the picture.

For long outages persisted breaker state would be helpful, but an extra dependency for marginal benefit.

## Output

```
In-process:  ring buffer (count-based) or bucketed time window;
             AtomicReference state; LongAdder counters; Semaphore HALF_OPEN
Distributed: Redis-backed counters + state pub/sub; rarely worth it
Persistence: typically none; cold start = CLOSED
```
