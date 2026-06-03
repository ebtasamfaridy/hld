# 10 · Rate Limiter — Design Patterns

## 1. Strategy — `Algorithm`
Plug Token Bucket / Leaky Bucket / Fixed / Sliding Log / Sliding Counter. Selectable per key family.

## 2. Strategy — `KeyExtractor`
Each app composes its own (IP + user + route + custom). `CompositeKeyExtractor` chains them.

## 3. Strategy — `Store`
In-memory or Redis. Same interface; algorithm picks how to use it (Hash vs ZSet).

## 4. Adapter — `RedisStore` Lua scripts
Wraps the Redis client; loads scripts at startup; calls EVALSHA. Scripts encapsulate atomicity.

## 5. Discriminated union — `Decision`
Sealed `Allow | Deny`. Caller pattern-matches. Enables exhaustive handling and richer Deny payloads (retryAfter + scope).

## 6. Circuit Breaker — Redis fallback
Wrap RedisStore calls in a breaker; fail-open on persistent failures.

## 7. Decorator — `LoggingRateLimiter`, `MetricsRateLimiter`
Wrap the base limiter to add cross-cutting concerns. The logging decorator emits to the audit log on Deny.

## 8. Builder — `RateLimiterBuilder`
Wire up the algorithm registry, key extractor, store, breaker, decorators.

## 9. Template Method — `BaseAlgorithm`
Common pre/post (record metric, audit deny). Each subclass implements `doCheck(...)`.

## 10. Specification (lite) — `LimitConfig` lookup
A `LimitConfigProvider` looks up config by key family + scope. Implementations: static map, DB-backed, hot-reloadable.

## What we avoid

| Pattern | Why not |
| --- | --- |
| Visitor over Decision | Sealed switch is cleaner |
| Singleton RateLimiter | Multiple instances per app (different scopes) common |
| Subclass-per-algorithm-with-state | State lives in Store, not in the Algorithm class |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Algorithm | Pluggable rate-limit algo |
| Strategy | KeyExtractor | Pluggable key derivation |
| Strategy | Store | In-mem vs Redis |
| Adapter | RedisStore Lua | Atomic Redis ops |
| Sealed | Decision | Allow/Deny discrimination |
| Circuit Breaker | Redis dep | Fail-open on outages |
| Decorator | Logger / Metrics | Cross-cutting concerns |
| Builder | RateLimiterBuilder | Wiring |

## Output

The big patterns: **Strategy** (everywhere) and **Adapter + Lua** (the magic for distributed correctness).
