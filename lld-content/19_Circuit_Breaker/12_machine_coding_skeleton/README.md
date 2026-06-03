# 12 · Circuit Breaker — Machine Coding Skeleton

In-process circuit breaker with count-based sliding window, failure & slow-call thresholds, automatic and manual transitions, and listeners.

```
src/main/java/com/circuitbreaker/
├── api/         CircuitBreaker, CircuitBreakerConfig, State, AcquireResult,
│                CallNotPermittedException, ExceptionClassifier, EventListener
├── core/        StandardCircuitBreaker
├── window/      SlidingWindow (interface), CountWindow
├── policy/      Bulkhead (semaphore wrapper)
├── registry/    CircuitBreakerRegistry, Decorators
└── Main.java
```

## Demo
1. Create a breaker for "user-service" with thresholds.
2. Call it 50 times where 60 % fail → trips to OPEN.
3. Show that subsequent calls reject immediately (CallNotPermittedException).
4. Wait for `waitDuration`; show probe phase (HALF_OPEN) admits N probes.
5. Probes succeed → CLOSED. Or one fails → OPEN again.
6. Demo Bulkhead refusing 11th concurrent call when limit=10.
