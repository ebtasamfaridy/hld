# 09 · Circuit Breaker — State Machines

## Primary state machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN : failureRate ≥ threshold AND calls ≥ minCalls
    CLOSED --> OPEN : slowCallRate ≥ threshold AND calls ≥ minCalls
    OPEN --> HALF_OPEN : after waitDurationInOpenState
    HALF_OPEN --> CLOSED : success rate of permitted probes ≥ threshold
    HALF_OPEN --> OPEN : a probe failed OR success rate insufficient
    CLOSED --> FORCED_OPEN : manual transitionToOpen()
    OPEN --> FORCED_OPEN : manual
    HALF_OPEN --> FORCED_OPEN : manual
    FORCED_OPEN --> CLOSED : reset()
    CLOSED --> FORCED_CLOSED : manual
    FORCED_CLOSED --> CLOSED : reset()
    CLOSED --> DISABLED : disable()
    DISABLED --> CLOSED : enable()
```

`FORCED_*` and `DISABLED` are administrative overrides:
- `FORCED_OPEN`: rejects everything; ignores signal.
- `FORCED_CLOSED`: allows everything; ignores signal.
- `DISABLED`: allows everything; **and** doesn't record. Useful for "shadow" mode.

## Window state (count-based)

```mermaid
stateDiagram-v2
    [*] --> EMPTY
    EMPTY --> FILLING : recordSuccess/Failure/Slow
    FILLING --> FULL : after N records
    FULL --> FULL : new record evicts oldest
    FULL --> EMPTY : reset() (e.g., on state change)
```

The "minimum calls" check matters only while in `EMPTY` or `FILLING`.

## Window state (time-based)

```mermaid
stateDiagram-v2
    [*] --> ROLLING
    ROLLING --> ROLLING : every second, the bucket index advances
    Note right of ROLLING : oldest bucket gets recycled (zeroed)
```

The window never "fills" the way count-based does. It's always rolling over the last N seconds.

## HALF_OPEN probe state

```mermaid
stateDiagram-v2
    [*] --> WAITING_FOR_PROBES
    WAITING_FOR_PROBES --> COLLECTING_RESULTS : a probe completed
    COLLECTING_RESULTS --> COLLECTING_RESULTS : another probe completed
    COLLECTING_RESULTS --> CLOSED_DECIDED : success rate sufficient
    COLLECTING_RESULTS --> OPEN_DECIDED    : a probe failed
    CLOSED_DECIDED --> [*]
    OPEN_DECIDED --> [*]
```

In simpler implementations, **any** failed probe → OPEN. In more lenient ones, you wait until N probes complete and decide based on rate.

## Output

```
Primary:    CLOSED → OPEN → HALF_OPEN → CLOSED|OPEN; manual overrides via FORCED_*
Window:     count-based EMPTY → FILLING → FULL → FULL (eviction); reset on state change
Probe:      WAITING → COLLECTING → CLOSED|OPEN_DECIDED
```
