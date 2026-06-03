# 08 · Circuit Breaker — Sequence Diagrams

## 1. CLOSED — successful call

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant CB as CircuitBreaker (CLOSED)
    participant Win as SlidingWindow
    participant Down as Downstream

    Caller->>CB: acquire()
    CB-->>Caller: ALLOWED
    Caller->>Down: call()
    Down-->>Caller: result
    Caller->>CB: onSuccess(50ms)
    CB->>Win: recordSuccess()
    CB->>CB: failureRate = 5/100 = 5% < threshold → stay CLOSED
```

## 2. CLOSED → OPEN: failure-rate trip

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant CB as CircuitBreaker
    participant Win

    loop calls 1..100
      Caller->>CB: acquire / call / onError
      CB->>Win: recordFailure
    end
    CB->>Win: metrics() → failures=60, calls=100
    CB->>CB: failureRate = 0.6 ≥ 0.5 AND calls ≥ minimumCalls
    CB->>CB: CAS state CLOSED → OPEN
    CB->>CB: openUntilNanos = nanoTime() + waitDuration
    Note over CB: future acquires will reject for `waitDuration`
```

## 3. OPEN: short-circuit reject

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant CB as CircuitBreaker (OPEN)

    Caller->>CB: acquire()
    CB->>CB: state == OPEN, openUntil > now → REJECTED
    CB-->>Caller: AcquireResult.REJECTED
    Caller->>Caller: throw CallNotPermittedException OR fallback
```

## 4. OPEN → HALF_OPEN auto-transition

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant CB as CircuitBreaker (OPEN)

    Note over CB: time has passed — openUntil < now()

    Caller->>CB: acquire()
    CB->>CB: state == OPEN AND openUntil < now()
    CB->>CB: CAS OPEN → HALF_OPEN, permits = N
    CB->>CB: tryAcquire permit → ALLOWED
    CB-->>Caller: ALLOWED (probe)
```

## 5. HALF_OPEN — success path → CLOSED

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Caller 1
    participant CB as CircuitBreaker (HALF_OPEN, permits=10)
    participant Down

    par 10 probes
      C1->>CB: acquire (permit)
      CB-->>C1: ALLOWED
      C1->>Down: call
      Down-->>C1: success
      C1->>CB: onSuccess
    end
    CB->>CB: 10 probes succeeded → CAS HALF_OPEN → CLOSED
    CB->>CB: reset window
    CB->>CB: emit StateChange
```

## 6. HALF_OPEN — a probe fails → OPEN

```mermaid
sequenceDiagram
    autonumber
    participant C as Caller
    participant CB as CircuitBreaker (HALF_OPEN)
    participant Down

    C->>CB: acquire (probe)
    CB-->>C: ALLOWED
    C->>Down: call
    Down--xC: error
    C->>CB: onError(throwable)
    CB->>CB: classifier says failure
    CB->>CB: CAS HALF_OPEN → OPEN, openUntil = now + waitDuration
    Note over CB: subsequent acquires reject immediately
```

## 7. Manual force-open

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin / DevOps
    participant CB

    Adm->>CB: transitionToForcedOpen()
    CB->>CB: state = FORCED_OPEN, emit event
    Note over CB: now all acquires reject — no automatic transitions

    Adm->>CB: reset()
    CB->>CB: state = CLOSED, window cleared
```

## 8. Combined: Bulkhead + CB + Retry + Timeout

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant BH as Bulkhead
    participant CB as CircuitBreaker
    participant RT as Retry
    participant TO as Timeout
    participant Down

    App->>BH: acquire (semaphore)
    BH-->>App: permit
    App->>CB: acquire
    CB-->>App: ALLOWED
    App->>RT: call with retry
    RT->>TO: attempt 1
    TO->>Down: call (with deadline)
    Down-->>TO: timeout
    TO-->>RT: TimeoutException
    RT->>RT: shouldRetry → yes
    RT->>TO: attempt 2
    TO->>Down: call
    Down-->>TO: success
    TO-->>RT: ok
    RT-->>App: ok
    App->>CB: onSuccess (slow)
    App->>BH: release
```

Order is critical: Bulkhead first (so we never call CB if at concurrency limit); CB second; Retry third (so retries are subject to CB); Timeout innermost.

## Output

```
Hot path:    CLOSED acquire → caller pays ~ns
Trip:        failure rate ≥ threshold + min calls → CAS CLOSED → OPEN
Wait:        OPEN until nanoTime > openUntil → CAS OPEN → HALF_OPEN
Probe:       HALF_OPEN allows N probes; success → CLOSED, fail → OPEN
Manual:      force-open / force-close / reset for DevOps
Combined:    Bulkhead → CircuitBreaker → Retry → Timeout
```
