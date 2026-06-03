# 09 · Rate Limiter — State Machines

## Per-key Token Bucket (logical)

```mermaid
stateDiagram-v2
    [*] --> FULL : key first seen (initialized at burst)
    FULL --> PARTIAL : check (allow, tokens > 0)
    PARTIAL --> PARTIAL : check (allow)
    PARTIAL --> DEPLETED : check (allow, tokens hits 0)
    DEPLETED --> DEPLETED : check (deny)
    DEPLETED --> PARTIAL : refill ticks bring tokens > 0
    PARTIAL --> FULL : long idle (tokens reach burst)
```

States are *informational*; the actual data is `tokens: double`. Useful for debugging dashboards.

## Circuit breaker for Redis dependency

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN : N consecutive failures (e.g., 5 in 10s)
    OPEN --> HALF_OPEN : after cooldown (e.g., 30s)
    HALF_OPEN --> CLOSED : probe succeeds
    HALF_OPEN --> OPEN : probe fails
```

When OPEN, the limiter fails-open without hitting Redis.

## Decision discriminated union

```mermaid
stateDiagram-v2
    [*] --> Allow : check passed
    [*] --> Deny  : limit exceeded
    note right of Deny
      Carries: retryAfter, limit, scope
    end note
```

Caller exhaustively pattern-matches.

## Output

```
Token Bucket:    FULL ↔ PARTIAL ↔ DEPLETED (logical view of tokens)
Breaker:         CLOSED → OPEN → HALF_OPEN → CLOSED
Decision:        sealed Allow | Deny
```
