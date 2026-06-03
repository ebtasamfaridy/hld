# 07 · Circuit Breaker — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Enums =====
    class State {
      <<enumeration>>
      CLOSED
      OPEN
      HALF_OPEN
      FORCED_OPEN
      FORCED_CLOSED
      DISABLED
    }
    class AcquireResult {
      <<enumeration>>
      ALLOWED
      REJECTED
    }

    %% ===== Functional interface =====
    class ExceptionClassifier {
      <<functional interface>>
      +isFailure(throwable) boolean
      +all() ExceptionClassifier$
      +ignoring(classes) ExceptionClassifier$
    }

    %% ===== Config =====
    class CircuitBreakerConfig {
      -String name
      -int windowSize
      -int minimumCalls
      -double failureRateThreshold
      -double slowCallRateThreshold
      -Duration slowCallThreshold
      -Duration waitDurationInOpen
      -int permittedCallsInHalfOpen
      -ExceptionClassifier classifier
      +builder(name) Builder$
    }
    class Builder {
      -String name
      -int windowSize
      -int minimumCalls
      -double failureRateThreshold
      -double slowCallRateThreshold
      -Duration slowCallThreshold
      -Duration waitDurationInOpen
      -int permittedCallsInHalfOpen
      -ExceptionClassifier classifier
      +windowSize(n) Builder
      +failureRateThreshold(r) Builder
      +slowCallRateThreshold(r) Builder
      +slowCallThreshold(d) Builder
      +waitDurationInOpen(d) Builder
      +permittedCallsInHalfOpen(n) Builder
      +classifier(c) Builder
      +build() CircuitBreakerConfig
    }
    CircuitBreakerConfig *-- Builder
    CircuitBreakerConfig o-- "1" ExceptionClassifier

    %% ===== Window =====
    class SlidingWindow {
      <<interface>>
      +recordSuccess()
      +recordFailure()
      +recordSlow()
      +reset()
      +snapshot() Snapshot
    }
    class Snapshot {
      <<record>>
      +int totalCalls
      +int failures
      +int slowCalls
      +failureRate() double
      +slowCallRate() double
    }
    class CountWindow {
      -AtomicIntegerArray slots
      -AtomicInteger nextIndex
      -int size
      +recordSuccess()
      +recordFailure()
      +recordSlow()
      +reset()
      +snapshot() Snapshot
    }
    SlidingWindow <|.. CountWindow
    SlidingWindow *-- Snapshot

    %% ===== Listener =====
    class EventListener {
      <<interface>>
      +onStateChange(name, from, to, reason)
      +onCallSuccess(name, durationNs)
      +onCallFailure(name, durationNs, throwable)
      +onCallSlow(name, durationNs)
      +onCallRejected(name)
    }

    %% ===== Exception =====
    class CallNotPermittedException {
      +CallNotPermittedException(name)
    }

    %% ===== Core CircuitBreaker =====
    class CircuitBreaker {
      <<interface>>
      +name() String
      +state() State
      +config() CircuitBreakerConfig
      +acquire() AcquireResult
      +onSuccess(durationNanos)
      +onError(durationNanos, throwable)
      +onRejected()
      +transitionToOpen()
      +transitionToClosed()
      +transitionToForcedOpen()
      +transitionToDisabled()
      +reset()
      +addListener(listener)
      +execute(supplier) T
    }
    class StandardCircuitBreaker {
      -CircuitBreakerConfig config
      -SlidingWindow window
      -AtomicReference~State~ stateRef
      -long openUntilNanos
      -Semaphore halfOpenPermits
      -AtomicInteger halfOpenSuccesses
      -List~EventListener~ listeners
      +acquire() AcquireResult
      +onSuccess(durationNanos)
      +onError(durationNanos, throwable)
      +onRejected()
      +transitionToOpen()
      +transitionToClosed()
      +transitionToForcedOpen()
      +transitionToDisabled()
      +reset()
      +addListener(listener)
    }
    CircuitBreaker <|.. StandardCircuitBreaker
    StandardCircuitBreaker o-- "1" SlidingWindow
    StandardCircuitBreaker o-- "1" CircuitBreakerConfig
    StandardCircuitBreaker o-- "*" EventListener

    %% ===== Bulkhead =====
    class Bulkhead {
      -String name
      -Semaphore sem
      -long acquireTimeoutMs
      +execute(supplier) T
    }
    class BulkheadFullException

    %% ===== Registry =====
    class CircuitBreakerRegistry {
      -ConcurrentMap~String,CircuitBreaker~ breakers
      +breaker(config) CircuitBreaker
      +get(name) CircuitBreaker
      +all() Collection~CircuitBreaker~
    }
    CircuitBreakerRegistry o-- "*" CircuitBreaker
```

---



## Core class diagram

```mermaid
classDiagram
    class CircuitBreaker {
      <<interface>>
      +execute(supplier) T
      +acquire() AcquireResult
      +onSuccess(ns)
      +onError(ns, t)
      +state() State
      +metrics() Metrics
    }

    class StandardCircuitBreaker {
      -config: Config
      -window: SlidingWindow
      -state: AtomicReference~State~
      -openUntilNanos: long
      -halfOpenPermits: Semaphore
      -listeners: List~EventListener~
      +acquire/onSuccess/onError/...
    }

    class Config {
      windowKind, windowSize, minCalls,
      failureRateThreshold, slowCallRateThreshold,
      slowCallThreshold, waitInOpen,
      permittedCallsInHalfOpen,
      classifier
    }

    class State {
      <<enum>>
      CLOSED OPEN HALF_OPEN DISABLED FORCED_OPEN FORCED_CLOSED
    }

    class SlidingWindow {
      <<interface>>
      +recordSuccess() / recordFailure() / recordSlow()
      +metrics() Metrics
    }
    class CountWindow {
      -slots: AtomicReferenceArray~Outcome~
      -nextIndex: AtomicInteger
    }
    class TimeWindow {
      -buckets: Bucket[60]
      -bucketSizeNanos: long
    }
    SlidingWindow <|.. CountWindow
    SlidingWindow <|.. TimeWindow

    class Metrics {
      calls, failures, slowCalls,
      failureRate, slowCallRate,
      stateSnapshot
    }

    class ExceptionClassifier {
      <<interface>>
      +isFailure(t) boolean
    }

    class CircuitBreakerRegistry {
      -breakers: ConcurrentMap~String, CircuitBreaker~
      -defaults: Config
      +breaker(name, config?) CircuitBreaker
      +all() Collection
    }

    class EventListener {
      <<interface>>
      +onStateChange(from, to, reason)
      +onCallSuccess/Failure/Slow/Rejected
    }

    class Decorators {
      +ofSupplier(cb, supplier)
      +ofSupplier(cb, bulkhead, retry, timeout, supplier)
    }

    class Bulkhead {
      -semaphore: Semaphore
      +execute(supplier)
    }

    CircuitBreaker <|.. StandardCircuitBreaker
    StandardCircuitBreaker o-- Config
    StandardCircuitBreaker o-- SlidingWindow
    StandardCircuitBreaker o-- ExceptionClassifier
    StandardCircuitBreaker o-- EventListener
    CircuitBreakerRegistry o-- CircuitBreaker
    Decorators ..> CircuitBreaker
    Decorators ..> Bulkhead
```

## Package layout (`com.circuitbreaker`)

```
api/        CircuitBreaker, Config (+ Builder), State, Metrics,
            CallNotPermittedException, ExceptionClassifier, EventListener
core/       StandardCircuitBreaker
window/     SlidingWindow, CountWindow, TimeWindow
policy/     ExceptionClassifier impls; Bulkhead (semaphore)
registry/   CircuitBreakerRegistry, Decorators
```

## Why these abstractions

### `CircuitBreaker` as an interface
Test/mocked implementations exist; a `NoopCircuitBreaker` for when the user wants to disable without removing the wiring.

### `SlidingWindow` as a strategy
Two implementations cover 99 %: count-based (simple, cheap) and time-based (semantically clear). Same interface; same set of operations.

### `Config` as immutable + builder
Once a breaker is constructed, its config is final. Reconfigure = create a new breaker.

### `EventListener` as a thin observer
Multiple listeners; subscribe/unsubscribe. Listeners must not throw — caught by breaker.

### `ExceptionClassifier`
Pluggable: count `IOException` and `TimeoutException` but not `BadRequestException`. Default classifier counts everything.

### `Registry` for shared default config
Many breakers share a default; per-breaker overrides for special cases.

### `Decorators` builder
Combines `Bulkhead → CircuitBreaker → Retry → Timeout`. Order matters; the builder hides the right ordering.

## Output

```
Layered:  api → core (StandardCircuitBreaker) → window (Count/Time) → registry
Strategy: SlidingWindow, ExceptionClassifier
Pattern:  CircuitBreaker is the policy; Window is the data structure;
          Decorators combine resilience primitives in the right order
```
