# 02 · Circuit Breaker — Capacity Estimation

## Per-process load

```
Hot service:       50 K req/s
Wraps 5 downstreams via 5 breakers
Total breaker decisions/sec: 250 K
Decision cost target:        < 1 µs
Total CPU spent on breakers: 250 K × 1 µs = 250 ms/sec ≈ 25 % of 1 core
```

That's tolerable but not great. Need < 100 ns per decision in steady state.

## What dominates the cost?

Hot path:
1. Check state (volatile read of an enum). ~2 ns.
2. If CLOSED, allow.
3. If OPEN, check expiry (volatile read of timestamp + compare). ~5 ns.
4. If HALF_OPEN, semaphore acquire/release. ~50 ns.

Average steady state (CLOSED + occasional record): ~10 ns. **30× headroom.**

## Sliding window cost

Per call: increment a counter or push into a ring buffer. ~10 ns.

For time-based windows partitioned into buckets (e.g., 60 buckets × 1 sec each), the "current bucket" is found via `(nowSec mod 60)`. Increment via `LongAdder` for contention-free counting.

## Memory

Per breaker:
- 4 enum/state words.
- Ring buffer of N call records (e.g., 100 × 16 B = 1.6 KB) **or**
- Bucketed counters (60 buckets × 16 B = 1 KB).
- Listener list (CoW; small).

≈ 2–4 KB per breaker. With 1000 breakers across a service, ~4 MB.

## Concurrency analysis

- Multiple threads writing to the window simultaneously.
- Multiple threads reading state simultaneously.
- One thread (or any) may transition state.

Patterns:
- **State**: `volatile State`. Reads cheap; writes via CAS for transitions.
- **Counters**: `LongAdder` (one per outcome bucket per time-bucket).
- **Ring buffer**: `AtomicReferenceArray` with CAS-based slot assignment, or per-thread accumulators that get aggregated.

A bad design would be `synchronized` everywhere — would serialize every protected call.

## What forces design

1. **Sub-microsecond decision** → lock-free hot path.
2. **High write rate to window** → `LongAdder` or sharded counters.
3. **Time-based windows** → bucketing by time (not per-call timestamps).
4. **Concurrent state transitions** → CAS, not mutexes.
5. **Probe count in HALF_OPEN** → semaphore.

## Output

```
Hot path:    <100ns CLOSED decision; ~50ns HALF_OPEN with semaphore
Memory:      ~2KB per breaker; thousands of breakers fine
Concurrency: volatile state + LongAdder counters + CAS transitions; no mutexes
Time:        monotonic nanoTime() for windows
```
