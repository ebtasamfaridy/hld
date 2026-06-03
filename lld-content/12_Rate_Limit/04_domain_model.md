# 04 · Rate Limiter — Domain Model & Algorithms

## Domain types

```java
public record RateKey(String family, String value) {}     // e.g., ("ip", "1.2.3.4")

public record LimitConfig(String family, long maxTokens, long refillPerSecond, AlgorithmType algo) {}

public enum AlgorithmType { TOKEN_BUCKET, LEAKY_BUCKET, FIXED_WINDOW, SLIDING_LOG, SLIDING_COUNTER }

public sealed interface Decision permits Decision.Allow, Decision.Deny {
    record Allow(long remaining, Instant resetAt) implements Decision {}
    record Deny(Duration retryAfter, long limit, String violatedScope) implements Decision {}
}
```

## Algorithm 1 — Token Bucket (the default)

**Idea.** A bucket holds at most `burst` tokens. Tokens refill at rate `r` per second. Each request consumes 1 token (or `cost` tokens). Refused if not enough tokens.

```
state per key: { tokens: double, lastRefillTs: long }

allow(now):
  elapsed = now - lastRefillTs
  tokens = min(burst, tokens + elapsed * r)
  lastRefillTs = now
  if tokens >= 1:
    tokens -= 1
    return ALLOW(remaining = floor(tokens))
  else:
    return DENY(retryAfter = (1 - tokens) / r)
```

- **O(1)** per check.
- **State**: 16 bytes (two longs / two doubles).
- **Burst-friendly**: caller can use `burst` requests instantly if bucket is full.
- **Smooth** over time at average rate `r`.

This is the **production default** for almost every API.

## Algorithm 2 — Leaky Bucket

**Idea.** Requests enter a queue; the queue drains at constant rate `r`. If queue is full, deny.

```
state: { queue: List<Instant>, capacity: int }

allow(now):
  drain queue (remove items older than (now - 1/r))
  if queue.size < capacity:
    queue.add(now)
    return ALLOW
  else:
    return DENY(retryAfter = ...)
```

- **Smoother than Token Bucket** — output is always at rate ≤ r, no bursts allowed.
- **Useful for**: smoothing requests to a constrained downstream (e.g., a payments processor that doesn't tolerate bursts).
- **State**: O(capacity).

## Algorithm 3 — Fixed Window

**Idea.** Count requests in the current window of size `W`. Reset to 0 each new window.

```
state: { count, windowStart }

allow(now):
  if now - windowStart >= W:
    count = 0; windowStart = now
  if count < limit:
    count++
    return ALLOW
  else:
    return DENY(retryAfter = W - (now - windowStart))
```

- **Simplest** to implement; **O(1)** state.
- **Boundary spike** problem: 100 requests at 0.999s of window 1, plus 100 at 0.001s of window 2 → 200 in 2 ms. Worse-than-promised behavior at boundary.
- **Useful for**: very rough limits where boundary spikes are tolerable.

## Algorithm 4 — Sliding Window Log

**Idea.** Keep timestamps of all requests in window. Count those in `[now - W, now]`.

```
state: { timestamps: SortedSet<long> }

allow(now):
  drop ts <= now - W
  if size < limit:
    add now
    return ALLOW
  else:
    return DENY(retryAfter = oldest + W - now)
```

- **Perfectly accurate.**
- **State O(limit)** — expensive at high RPS.
- Useful for: high-value, low-RPS protection (admin APIs).

## Algorithm 5 — Sliding Window Counter

**Idea.** Hybrid. Count the *current* window + an interpolated portion of the *previous* window:

```
weighted_count = currentWindowCount
               + previousWindowCount * (1 - elapsedInCurrent / W)
```

```
state: { currentWindow, currentCount, previousCount }
allow(now):
  rotate windows if window changed
  weighted = currentCount + previousCount * (1 - elapsed/W)
  if weighted < limit: currentCount++; return ALLOW
  else: return DENY
```

- **O(1) state**, **~5% accurate** vs sliding-log.
- **No boundary spike** problem.
- **Useful for**: balanced default; many production systems use this.

## When to use which

| Use case | Algorithm |
| --- | --- |
| Public API with bursts allowed | **Token Bucket** ✓ |
| Smoothing to a constrained downstream | Leaky Bucket |
| Quick-and-dirty | Fixed Window |
| Admin / billing | Sliding Log |
| Memory-constrained but accurate | Sliding Counter |

## Distributed correctness — Redis Lua

Naive: `INCR + EXPIRE` is not atomic — two clients can race past the limit. Solution: a **single Lua script** runs atomically.

### Token Bucket in Lua

```lua
-- KEYS[1] = bucket key
-- ARGV[1] = capacity, ARGV[2] = refill_rate (per ms), ARGV[3] = now_ms, ARGV[4] = cost
local cap   = tonumber(ARGV[1])
local rate  = tonumber(ARGV[2])
local now   = tonumber(ARGV[3])
local cost  = tonumber(ARGV[4])

local data = redis.call('HMGET', KEYS[1], 't', 'ts')
local tokens = tonumber(data[1])
local last   = tonumber(data[2])
if not tokens then tokens = cap; last = now end

local elapsed = math.max(0, now - last)
tokens = math.min(cap, tokens + elapsed * rate)
local allowed = tokens >= cost
if allowed then tokens = tokens - cost end

redis.call('HSET', KEYS[1], 't', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], 3600)

if allowed then
  return {1, tokens, 0}
else
  local retry_ms = math.ceil((cost - tokens) / rate)
  return {0, tokens, retry_ms}
end
```

This script runs atomically on the Redis primary. No race conditions.

## Output

```
Algorithms:    Token Bucket (default), Leaky Bucket (smooth), Fixed (rough),
               Sliding Log (accurate), Sliding Counter (cheap+accurate)
Distributed:   Single Lua script per check; atomic on Redis primary
Tradeoffs:     burst vs smoothness; accuracy vs memory
```
