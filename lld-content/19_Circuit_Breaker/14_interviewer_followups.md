# 14 · Circuit Breaker — Interviewer Follow-ups

## Q1. "What problem does a circuit breaker solve?"

A failing downstream causes pile-up: every caller waits for a timeout, threads pile up, the calling service runs out of threads/memory and dies too. Cascading failure.

The breaker fails fast: when the downstream is in trouble, the breaker rejects calls immediately. Callers free their threads. The calling service stays healthy. The downstream gets a chance to recover (its load drops).

---

## Q2. "Walk me through the 3 states."

- **CLOSED**: normal traffic. Outcomes feed the sliding window. If failure rate ≥ threshold AND calls ≥ minimum, trip to OPEN.
- **OPEN**: short-circuit. All calls reject immediately. After `waitDuration`, transition to HALF_OPEN.
- **HALF_OPEN**: probe phase. Allow N permitted calls. If they succeed, transition to CLOSED. If any fails, back to OPEN.

---

## Q3. "Why do we have a `minimumCalls` threshold?"

Imagine a window of 100 calls but only 2 calls have happened, both failed. Failure rate = 100%. That's noise — 2 calls isn't statistically significant.

`minimumCalls` (e.g., 10) prevents tripping until you have a meaningful sample. Without it, every cold start would trip the breaker.

---

## Q4. "Count-based vs time-based window — when do you pick which?"

- **Count-based**: simple, traffic-independent. Good for steady-traffic services.
- **Time-based**: reflects "the last N seconds." Good for variable-traffic services where call rates fluctuate.

Resilience4j defaults to count-based (size=100). Hystrix used time-based. Either works.

---

## Q5. "What's a 'slow call' and why does it matter?"

A successful call that took longer than `slowCallThreshold` (e.g., 2 s when normal is 200 ms). Slow calls indicate an unhealthy dependency even when it's technically returning success.

Treating slow calls as failures lets the breaker trip before timeouts hit. Pure failure-rate breakers can miss "everything is timing out at 10 s" situations.

---

## Q6. "How do state transitions work concurrently?"

`AtomicReference<State>` + CAS. Many threads might simultaneously notice "failure rate exceeded" and call `transitionToOpen()`. The CAS resolves this — exactly one wins. Threads that lose the CAS observe `OPEN` already set; their attempt is a harmless no-op.

```java
if (stateRef.compareAndSet(CLOSED, OPEN)) {
    openUntilNanos = nanoTime() + waitDuration;
    publish(StateChange);
}
```

---

## Q7. "Why a Semaphore for HALF_OPEN?"

Multiple threads see HALF_OPEN simultaneously. We want exactly N probes through; the rest reject.

A `Semaphore(N)` is ideal: `tryAcquire()` returns true for the first N callers, false after.

When we transition CLOSED → HALF_OPEN, we `release(N)` permits. When we transition out, `drainPermits()`.

---

## Q8. "What if a probe call hangs forever?"

The breaker doesn't time out probes itself. Compose with a `Timeout` decorator:

```
Timeout(5s) → CircuitBreaker → Downstream
```

The timeout cancels the call after 5 s; the breaker sees a `TimeoutException` (a "failure" per its classifier) and transitions back to OPEN.

---

## Q9. "Should every exception count as a failure?"

No. A 4xx is a *client* error — the dependency is healthy, the request is bad. Not a breaker concern.

`ExceptionClassifier` lets you specify: count `IOException`, `5xx`, `TimeoutException` but ignore `BadRequestException`, `ValidationException`, etc.

---

## Q10. "What's a Bulkhead and how does it relate?"

A bulkhead caps concurrent calls to a downstream (Semaphore with N permits). It's orthogonal to the breaker:
- Breaker: "is the downstream healthy?"
- Bulkhead: "are we drowning the downstream?"

Used together: `Bulkhead → CircuitBreaker → call`. Bulkhead first (so we don't even consult the breaker if at concurrency limit).

---

## Q11. "What's the right order: Retry, CircuitBreaker, Bulkhead, Timeout?"

```
Bulkhead → CircuitBreaker → Retry → Timeout → call
```

- **Bulkhead** outermost: no point checking anything else if at concurrency limit.
- **CircuitBreaker** next: short-circuit before retrying.
- **Retry** wraps the timed call so each attempt has its own timeout.
- **Timeout** innermost: enforces deadline per attempt.

If you put Retry outside CircuitBreaker, retries can re-trip the breaker during HALF_OPEN.

If you put Timeout outside Retry, the first slow call eats the entire deadline.

---

## Q12. "How would you make this distributed across many instances?"

Three options:
1. **Eventually consistent**: each instance has a local breaker; periodically sync counters via Redis.
2. **Strict**: every breaker decision is a Redis lookup. Defeats hot-path latency.
3. **Hybrid**: local breaker decides, but periodic gossip influences the threshold.

In practice, per-instance breakers work because failure modes are correlated. Distributed adds operational pain for marginal benefit.

---

## Q13. "If your breaker tripped during a routine deploy, what would you change?"

The deploy caused brief errors during the rollout. The breaker tripped, but the dependency is fine. Possible fixes:
- Increase `failureRateThreshold` (e.g., 0.5 → 0.7) — less sensitive.
- Increase `minimumCalls` — require more evidence.
- Shorten `waitDurationInOpenState` — recover faster.
- Add a `DeploymentInProgress` flag to suppress the breaker during deploys.

The right answer depends on the SLOs of the dependency.

---

## Q14. "How do you observe a circuit breaker in production?"

Listeners → metrics (Prometheus / Micrometer):
- `cb_calls_total{name, outcome}` counter.
- `cb_state{name}` gauge (0=CLOSED, 1=OPEN, 2=HALF_OPEN).
- `cb_state_transitions_total{name, from, to}`.
- Latency histogram per breaker.

Alerts:
- "Breaker X has been OPEN > 5 min" → page someone.
- "Breaker transitioned > 10 times in last hour" → flapping; investigate.

---

## Q15. "Common bug: the breaker trips on noise and never closes — what's wrong?"

Likely causes:
1. `minimumCalls` too low; trips on small samples.
2. `failureRateThreshold` too aggressive (e.g., 0.1 = trip on 10%).
3. `permittedCallsInHalfOpen` too low (1); a single tail-latency call flips back to OPEN.
4. Slow call threshold too aggressive; legitimate slow calls trigger the breaker.

Mitigations: raise minimum calls, raise threshold, raise probes, calibrate slow threshold.

---

## Output

```
Drilled:
- Why circuit breakers (cascade prevention)
- 3 states + transition rules
- minimumCalls floor for noise rejection
- Window types (count vs time)
- Slow calls as failures
- CAS-based concurrent transitions
- Semaphore for HALF_OPEN probes
- Combine with Timeout for hung probes
- Exception classification (skip 4xx)
- Bulkhead vs Breaker
- Decorator order: Bulkhead → CB → Retry → Timeout
- Distributed mode (rare; often unnecessary)
- Tuning during deploy noise
- Production observability
- Common bug: trip-then-stuck (config tuning)
```
