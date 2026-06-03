# 10 · Circuit Breaker — Design Patterns

## 1. State pattern (the obvious one)
The breaker has explicit states with distinct behavior. We model it via `enum State` + a switch on transitions; "full" State pattern (a class per state) is overkill.

## 2. Strategy — `SlidingWindow`
Count-based vs time-based. Same interface; pluggable.

## 3. Strategy — `ExceptionClassifier`
Only certain throwables count as failures. Pluggable predicate.

## 4. Decorator — `Decorators.ofSupplier(...)`
Wrap a supplier with breaker behavior. Stack additional decorators (Bulkhead, Retry, Timeout) — chain of decorators in a specific order.

## 5. Observer — `EventListener`
State changes + call outcomes broadcast to listeners (metrics, logs, alerts).

## 6. Registry — `CircuitBreakerRegistry`
Many breakers; lookup by name. Default config + per-name overrides.

## 7. Bulkhead pattern
A semaphore that caps concurrent calls. Different concept from breaker but routinely paired with it.

## 8. Token bucket / Semaphore for HALF_OPEN
Probe phase admits N callers via a semaphore; rejects others.

## 9. CAS / atomic state
State transitions are CAS on `AtomicReference<State>`. Multiple threads racing → exactly one wins; the others observe the new state.

## 10. Memento (lightweight)
On entering HALF_OPEN, snapshot probe count + permits. If we revert to OPEN, we don't need to restore — but the principle of "save current decision context" applies.

## 11. Failure isolation (architectural)
Each downstream gets its own breaker. A failing downstream B doesn't affect calls to downstream C.

## 12. Functional Lambda interfaces
`ExceptionClassifier`, `EventListener`, `Supplier`, `Function` — all SAM. Let users plug in lambdas without writing classes.

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| `synchronized` on every call | Murders the hot path |
| Counting failures as raw count (no rate) | Sensitive to traffic volume; rate is normalized |
| Coupling breaker to specific HTTP client | Library API is generic over `Supplier<T>` |
| Sharing a window across breakers | Each downstream has independent failure modes |
| Exposing internal state for read-modify-write | Caller can't cheat the state machine |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| State machine | Breaker primary | CLOSED/OPEN/HALF_OPEN with rules |
| Strategy | SlidingWindow / ExceptionClassifier | Pluggable behaviors |
| Decorator | `decorate(Supplier)` | Wrap calls with breaker logic |
| Observer | EventListener | Metrics, logs, alerts |
| Registry | CircuitBreakerRegistry | Per-name breakers |
| Bulkhead | Semaphore | Concurrency cap |
| Semaphore | HALF_OPEN permits | Probe rate limit |
| CAS | State transitions | Lock-free transitions under contention |
| Failure isolation | One breaker per downstream | Avoid cross-contamination |

## Output

```
The breaker is a STATE MACHINE + sliding-window STRATEGY + lock-free CAS transitions
+ DECORATOR over arbitrary callables. It composes with Bulkhead/Retry/Timeout via
chained decorators in a specific order (Bulkhead → CB → Retry → Timeout).
```
