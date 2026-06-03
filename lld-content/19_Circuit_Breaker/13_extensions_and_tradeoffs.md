# 13 · Circuit Breaker — Extensions & Tradeoffs

## Extensions

### 1. Time-based sliding window
Bucketed counters keyed by `nanoTime() / bucketSize`. Last 60 buckets = last 60 s.

### 2. Adaptive thresholds
Auto-tune `failureRateThreshold` based on baseline. If the baseline failure rate is normally 1%, trip at 5% (5×). Avoids hard-coded magic numbers.

### 3. Distributed breaker
Aggregate counters via Redis; coordinator emits state to all instances via pub/sub. Use sparingly — adds hot-path latency.

### 4. Per-instance health weighting
A breaker per (downstream, instance). If only some downstream instances are bad, only those breakers trip.

### 5. Hedged requests with breaker
Send the same request to two instances simultaneously; first response wins. Combine with breaker per-instance.

### 6. SLI-based thresholds
Replace simple failure rate with multi-signal SLOs: error rate ≥ X AND latency p99 ≥ Y.

### 7. Shadow / dark mode
DISABLED state: pass calls but record outcomes. Useful before flipping a real breaker on for a new dependency.

### 8. Backoff for HALF_OPEN
Don't always wait the same `waitDuration`. Use exponential backoff: 30 s → 60 s → 120 s when a probe fails.

### 9. Health-based trigger
Periodic background probe every N s. If healthy, eligible for HALF_OPEN sooner; if unhealthy, stay OPEN longer.

### 10. Composite breaker
A higher-level breaker that wraps several sub-breakers (DB, cache, external). Trips when M of N sub-breakers are open.

## Tradeoffs

### Count-based vs time-based window

| Count-based | Time-based |
| --- | --- |
| Independent of traffic volume | Reflects "the last N seconds" semantically |
| Fewer parameters | Need bucket size config |
| Cheaper memory | Slightly more bookkeeping |
| **Pick**: count-based for low-traffic; time-based for variable-traffic services |

### Failure rate vs consecutive failures

| Failure rate | Consecutive failures |
| --- | --- |
| Robust at scale | Sensitive to noise (one bad call trips) |
| Needs minimum-calls floor | Trip on K-in-a-row |
| **Pick**: failure rate + minimum calls is the modern answer |

### Per-instance vs distributed

Per-instance is simpler and almost always correct. Distributed adds operational complexity for a marginal benefit unless your topology is unusual.

### Permitted probes in HALF_OPEN

Too few (1) → bad luck (a slow tail call) re-opens the breaker.
Too many (50) → during outage you flood the dead dependency.
Default 10 with conservative wait time. Tune per dependency.

### Slow-call threshold

| Aggressive (200ms) | Lenient (5s) |
| --- | --- |
| Trips on degraded performance | Only trips on actual failures |
| Higher false-trip rate | Slower to detect |
| **Pick**: based on dependency's normal latency p99 |

### Combined order: Bulkhead → CB → Retry → Timeout

Always: cap concurrency first; then check breaker; then retry on transient failures; then enforce timeout per attempt. The reverse order causes:
- Retry inside CB → retries during HALF_OPEN can shut the breaker again on noise.
- Timeout outside Retry → first slow call kills the deadline before any retry.

## Open questions

- Should we treat 4xx as failures? (Usually no; client error, not service failure.)
- Should we pre-emptively HALF_OPEN before `waitDuration` if downstream becomes responsive (via a health check)? (Yes if health endpoint exists.)
- What's "slow"? (p99 baseline + 50% — context-specific.)
- How does breaker interact with auto-scaling? (When traffic shrinks, breaker might trip on noise; minimum-calls floor mitigates.)

## Output

```
Extensions:    time-based windows, adaptive thresholds, distributed state,
               per-instance breakers, SLO triggers, shadow mode,
               exponential HALF_OPEN backoff, composite breakers
Tradeoffs:     count vs time window; failure rate vs consecutive; 
               per-instance vs distributed; probe count; slow threshold;
               primitive ordering in resilience chain
Pre-decided:   per-instance breaker; failure rate + min calls; 10 probes;
               Bulkhead → CB → Retry → Timeout order
Open Qs:       4xx handling, pre-emptive HALF_OPEN, slow definition, autoscaling
```
