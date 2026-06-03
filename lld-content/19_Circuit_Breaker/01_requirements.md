# 01 · Circuit Breaker — Requirements

## Functional requirements

### Core
- Wrap any callable: `breaker.execute(() -> remoteCall())`.
- 3 states: **CLOSED** (calls pass through), **OPEN** (calls short-circuit immediately), **HALF_OPEN** (limited probes).
- Transitions:
  - CLOSED → OPEN: when failure rate ≥ threshold over the window.
  - OPEN → HALF_OPEN: after `waitDurationInOpenState`.
  - HALF_OPEN → CLOSED: when probe calls succeed at threshold.
  - HALF_OPEN → OPEN: when probe calls fail.
- **Sliding window** of recent calls (count-based or time-based).
- **Slow call rate**: a call is "slow" if duration > slowCallThreshold; treated like a failure for transition decisions.
- **Configurable thresholds**: failure rate %, slow call rate %, minimum calls before evaluating, ring buffer size, wait duration in OPEN, permitted calls in HALF_OPEN.
- **Per-resource registry**: name + config; lookup by name.
- **Manual control**: force-open, force-closed, reset.
- **Event publication**: state transitions, recorded calls, slow calls.
- **Decorator-style API**: wrap a `Supplier`, `Function`, `Runnable`.

### Required extensions
- **Fallback**: provide a fallback supplier when the breaker rejects.
- **Bulkhead**: semaphore for max concurrent calls (orthogonal but commonly bundled).
- **Exception classification**: which exceptions count as failures (not all do — e.g., a 4xx isn't an outage).

### Out of scope (V2)
- Distributed circuit breaker (state shared across instances).
- Adaptive thresholds (auto-tune based on load).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Decision latency | < 1 µs | Hot path on every protected call |
| Memory | < 1 KB per breaker | Many breakers (one per downstream) |
| Concurrency safety | full | Multiple threads call concurrently |
| State transition determinism | bug-free | Misbehavior is hard to debug |
| Observability | events + metrics | Critical to understand outages |

## Actors

```
Caller             - application code calling a downstream
CircuitBreaker     - decides allow / reject / probe
SlidingWindow      - records outcomes (success / failure / slow)
StateMachine       - CLOSED/OPEN/HALF_OPEN with transition rules
Registry           - holds breakers by name + config
Listener           - receives state-change / call events
```

## Edge cases

| Case | Handling |
| --- | --- |
| Long burst of successes after partial failure | Sliding window expires old failures; rate falls below threshold; CLOSED stays |
| Brief network blip | Window must have minimum-call threshold to avoid tripping on 2/2 failures |
| All calls slow but not failing | Slow-call rate triggers OPEN |
| Caller's exception isn't a "real failure" (4xx) | Exception classifier; only count as failure if matched |
| Probe call in HALF_OPEN crashes | Counts as a failed probe; transition back to OPEN |
| HALF_OPEN concurrent probes | Cap at `permittedCallsInHalfOpen`; reject above |
| Force open / force closed during transition | Manual override; events emitted |
| Window full and a new call arrives | Replace oldest record (ring buffer) |
| Clock skew | Use monotonic time (`System.nanoTime()`) for windows |
| Listener throws | Catch; log; never break the breaker |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| 3-state machine | ✓ | |
| Count-based window | ✓ | |
| Time-based window | ✓ | |
| Failure-rate trigger | ✓ | |
| Slow-call trigger | ✓ | |
| Manual force-open / close | ✓ | |
| Exception classification | ✓ | |
| Bulkhead (semaphore) | ✓ | |
| Listeners / events | ✓ | |
| Distributed state (Redis) | | ✓ |
| Adaptive thresholds | | ✓ |
| HTTP/2 concurrency limits | | ✓ |
| Per-instance health weighting | | ✓ |

## Output

```
Core:    3-state breaker; sliding window (count or time); failure rate + slow rate;
         exception classifier; permitted probes in HALF_OPEN; manual override
NFR:     <1µs decision; lock-free counters; deterministic transitions; observability
Edge:    minimum calls threshold, slow-call vs failure, listener errors,
         clock skew via monotonic time
```
