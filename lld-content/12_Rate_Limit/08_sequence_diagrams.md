# 08 · Rate Limiter — Sequence Diagrams

## 1. Single-key in-memory check (Token Bucket)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant RL as RateLimiter
    participant KE as KeyExtractor
    participant TB as TokenBucketAlgorithm
    participant ST as InMemoryStore

    App->>RL: check(req)
    RL->>KE: keysFor(req)
    KE-->>RL: [user:u-42]
    RL->>TB: check(user:u-42, cfg, cost=1, now)
    TB->>ST: get(user:u-42) → BucketState{tokens=84, ts}
    Note over TB: refill: tokens += elapsed * rate, capped at burst
    TB->>ST: update(user:u-42, BucketState{tokens=83.5, ts=now})
    TB-->>RL: Allow(remaining=83, resetAt=...)
    RL-->>App: Allow
```

## 2. Distributed check via Redis Lua

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant RL as RateLimiter
    participant TB as TokenBucketAlgorithm
    participant Redis

    App->>RL: check(req)
    RL->>TB: check(user:u-42, cfg, 1, now)
    TB->>Redis: EVALSHA <script_sha> 1 KEY ARG_cap ARG_rate ARG_now ARG_cost
    Note over Redis: Lua atomically reads, refills, decrements, writes
    Redis-->>TB: [allowed=1, tokens=83, retry_ms=0]
    TB-->>RL: Allow(83, ...)
    RL-->>App: Allow
```

The whole check is **one network round-trip**.

## 3. Multi-scope check, deny on second scope

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant RL as RateLimiter
    participant TB as TokenBucketAlgorithm

    App->>RL: check(req)
    RL->>TB: check(ip:1.2.3.4, cfg-ip, 1, now)
    TB-->>RL: Allow
    RL->>TB: check(user:u-42, cfg-user, 1, now)
    TB-->>RL: Deny(retryAfter=2s, scope=user)
    RL-->>App: Deny(retryAfter=2s, scope=user)
    Note over RL: IP scope already deducted a token → small unfairness
```

V2 with two-phase check would do:
1. Quote: read both buckets without mutating.
2. If both pass: atomic deduct on both via a multi-key Lua script.

## 4. Failure: Redis times out

```mermaid
sequenceDiagram
    autonumber
    participant RL as RateLimiter
    participant TB as TokenBucketAlgorithm
    participant Redis
    participant CB as CircuitBreaker

    RL->>TB: check(...)
    TB->>Redis: EVALSHA
    Redis--xTB: timeout
    TB->>CB: record fail
    CB-->>TB: open after N fails
    TB-->>RL: Allow(remaining=-1) -- fail-open
    RL-->>App: Allow
    Note over RL: alert raised, subsequent calls bypass Redis
```

When the breaker is OPEN, we skip Redis entirely and return Allow. After a probe interval, try once; restore on success.

## 5. Cost-weighted check (heavy endpoint)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant RL as RateLimiter
    participant TB as TokenBucketAlgorithm

    App->>RL: check(req, cost=5)
    RL->>TB: check(user:u-42, cfg, 5, now)
    Note over TB: tokens=4 < 5 → deny
    TB-->>RL: Deny(retryAfter = (5-4)/rate)
    RL-->>App: Deny
```

The cost factor allows weighting heavy endpoints without requiring per-endpoint configs.

## Output

```
Single-key:    one Lua call (Redis) or one Map op (in-memory)
Multi-scope:   sequential checks; deny on first fail
Failure:       circuit breaker → fail-open
Cost-weighted: pass cost > 1 to algorithm
```
