# 06 · Rate Limiter — API Design

## Library API (programmatic)

```java
public interface RateLimiter {
    Decision check(Request request);
    Decision check(Request request, int cost);    // for cost-weighted variants
}

public sealed interface Decision permits Allow, Deny {
    record Allow(long remaining, Instant resetAt) {}
    record Deny(Duration retryAfter, long limit, String violatedScope) {}
}

public interface KeyExtractor {
    List<RateKey> keysFor(Request r);     // multiple keys = multiple scopes
}

public interface LimitConfigProvider {
    LimitConfig configFor(RateKey key);
}
```

## HTTP middleware

Standard headers on every response:

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 873
X-RateLimit-Reset: 1714437600         (epoch seconds)
X-RateLimit-Scope: user
```

On deny:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1714437612
X-RateLimit-Scope: user

{
  "type": ".../rate-limit",
  "title": "Rate limit exceeded",
  "scope": "user",
  "retry_after_seconds": 12
}
```

## Admin API (V2)

```http
GET  /v1/limits                     # list configs
PUT  /v1/limits/{family}            # set config
GET  /v1/limits/{family}/keys/{key} # current state

POST /v1/limits/override            # tempbypass per key
{
  "key_family": "user",
  "key_value": "u-42",
  "until": "2026-04-29T20:00Z",
  "reason": "incident-12345"
}
```

## Idempotency

The limiter check is **not idempotent** — every check counts a request. There's no retry semantics; if the caller retries a denied request later, that's a new check.

For batch operations, the *batch endpoint* must check once for the entire batch (cost-weighted), not once per item.

## Errors / debug

```
GET /v1/limits/{family}/keys/{key}
→ {
   "key": { "family": "user", "value": "u-42" },
   "algo": "TOKEN_BUCKET",
   "config": { "max_tokens": 100, "refill_per_sec": 10 },
   "state": { "tokens": 84.3, "last_refill": "..." },
   "stats_24h": { "allows": 12345, "denies": 27 }
}
```

This is the operator's debugging view.

## Output

```
Library:     RateLimiter.check(req) → Allow|Deny
HTTP:        429 + Retry-After + X-RateLimit-* headers
Admin:       configs CRUD, per-key state, temp-overrides
```
