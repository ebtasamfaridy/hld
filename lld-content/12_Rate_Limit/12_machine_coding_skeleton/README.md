# 12 · Rate Limiter — Machine Coding Skeleton

In-memory implementations of all five algorithms; Redis layer is sketched as Lua resource files (no live Redis client).

```
src/main/java/com/ratelimit/
├── domain/      RateKey, LimitConfig, Decision (sealed), Request
├── algorithm/   Algorithm + TokenBucket / LeakyBucket / FixedWindow / SlidingLog / SlidingCounter
├── store/       Store + InMemoryStore (and a sketched RedisStore facade)
├── middleware/  KeyExtractor, CompositeKeyExtractor
├── RateLimiter.java
└── Main.java
```

Resource: `src/main/resources/lua/token_bucket.lua` — the production Redis script.

## Demo

1. Set up Token Bucket: 5 tokens, 1 token/sec refill.
2. Burst 6 requests: first 5 ALLOW, 6th DENY.
3. Sleep 2s; 2 more should ALLOW.
4. Composite (IP + user); deny on user scope when IP allows.
