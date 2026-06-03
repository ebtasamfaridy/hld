# 01 · Rate Limiter — Requirements

## Functional requirements

### Core
- Limit requests by a configurable **key** (IP, user ID, API key, route, or composite).
- Support multiple **scopes** simultaneously: per-IP + per-user + per-API-key + global. Most-restrictive applies.
- Configurable **rate** and **burst** (capacity) per key family.
- Return clear **deny** signal with `Retry-After` and `X-RateLimit-*` headers.
- **Tunable algorithms** (Token Bucket / Leaky Bucket / Fixed / Sliding Log / Sliding Counter).
- Work in:
  - **Single-process** (in-memory map).
  - **Distributed** (Redis cluster).
- Operate at **request gateway** (API gateway / sidecar / middleware in app).

### Out of scope
- Pricing-tier business logic (consumed by limits, not implemented here).
- Anti-abuse beyond rate limits (CAPTCHAs, fingerprinting).
- Quota / usage-based billing (related but distinct).

### Extensions
- **Cost-weighted** limits (a heavy endpoint costs 5 tokens, light = 1).
- **Hierarchical limits** (per-org → per-user, with each level configured).
- **Adaptive** limits (auto-tune based on current load).
- **Rate-limit override** for trusted callers (admin / partner).
- **Long-poll-friendly** algorithms (waiting clients yield tokens earlier).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Latency added | < 1 ms p99 | Limiter on every request must be invisible |
| Throughput per node | 100 K decisions / sec | Big API gateways |
| Distributed accuracy | within ±5 % at boundary | Counters are approximate |
| Failure mode | fail-open (default) | Redis outage shouldn't take API down |
| Memory per key | < 100 B | Millions of keys |
| Scale | 1 M unique keys / minute | Per-IP at internet scale |

## Actors

```
Caller (client)        - sends requests
RateLimiter            - decides allow / deny
KeyExtractor           - turns request into one or more keys
Algorithm              - the math
Store                  - state per key (in-memory or Redis)
LimitConfig            - per-key-family rate + burst + algo
```

## Edge cases

| Case | Handling |
| --- | --- |
| Clock skew across nodes | Use Redis server time (`TIME`) inside Lua, or NTP-synced clocks |
| Redis outage | Fail-open with audit log + alert |
| Burst at clock boundary (fixed window) | Switch to sliding-window algorithm |
| Cost-weighted: req with cost > burst | Always reject; configurable behavior |
| Unicode key | Normalize before hashing |
| Missing identifier | Apply IP-only limit |
| Distributed deny + retry headers stale | Use `now` from Redis, not local |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Single algorithm (Token Bucket) | ✓ | |
| Multi-key composite | ✓ | |
| Redis Lua | ✓ | |
| Multi-algorithm pluggable | ✓ | |
| Hierarchical limits | | ✓ |
| Cost-weighted | | ✓ |
| Adaptive | | ✓ |
| Override tokens | | ✓ |

## Output

```
Actors:    Caller, RateLimiter, KeyExtractor, Algorithm, Store, LimitConfig
Core FR:   per-key limits, multi-scope composite, multiple algorithms, Redis-backed Lua
NFR:       <1ms p99, 100K dec/sec, fail-open, 1M unique keys/min
Edge:      clock skew, Redis outage, boundary spikes, cost > burst
```
