# 14 · Rate Limiter — Interviewer Follow-ups

## Q1. "Why Token Bucket as the default?"

It allows controlled bursts (caller can use up to `burst` tokens immediately) while enforcing a long-term average rate `r`. State is O(1). Most APIs prefer this UX over strictly smoothed Leaky Bucket.

If the downstream cannot tolerate bursts (a slow payment processor), Leaky Bucket is the right default for that endpoint.

---

## Q2. "How do you make this work across 100 pods?"

Centralized Redis with a single Lua script per check. The script atomically reads, refills, decrements, writes. All pods see the same global state through Redis.

Without the Lua script, multi-step `INCR + EXPIRE` races between pods.

---

## Q3. "Walk through the Token Bucket Lua script."

```lua
1. HMGET to fetch (tokens, lastTs).
2. If absent, initialize at full capacity.
3. Compute elapsed * rate; cap at capacity.
4. If tokens >= cost, decrement; allowed=1.
5. HSET back. EXPIRE (TTL).
6. Return [allowed, remaining, retryAfterMs].
```

The whole thing is atomic because Lua runs single-threaded on the Redis primary.

---

## Q4. "What if Redis is down?"

Fail-open. Wrap the Redis call in a circuit breaker. After N consecutive failures, the breaker opens and we return `Allow` without hitting Redis. Alerts fire. Periodic probes try to close the breaker.

The alternative (fail-closed) means our API goes down when Redis goes down — usually unacceptable.

For billing-grade quotas, fail-closed is the right call; document explicitly.

---

## Q5. "Multi-scope check (per-IP + per-user). What if one passes and one fails?"

Sequential check; deny on first fail. The earlier scopes have already deducted tokens for this request. Small unfairness, accepted as tradeoff.

For revenue-critical APIs, V2 has a two-phase check: quote all, deduct atomically only if all pass. Requires hash-tagged keys (single shard) and a multi-key Lua script.

---

## Q6. "Boundary spike with Fixed Window — explain it."

100 req/min limit, window starts at minute boundary. Caller fires 100 requests at 10:00:59.999 (window 1 still has slots). Then 100 more at 10:01:00.001 (new window). 200 requests in 2 ms — way more than the spec's intent.

Sliding Window Counter or Token Bucket avoid this: the time-weight function makes the boundary smooth.

---

## Q7. "How do you choose the right algorithm?"

Decision tree:
- Need bursts allowed? → Token Bucket.
- Need strict smoothing? → Leaky Bucket.
- Memory super tight? → Sliding Window Counter.
- Need exact accuracy on low-volume admin API? → Sliding Window Log.
- Quick prototype, boundaries OK? → Fixed Window.

Default: Token Bucket. 95 % of cases.

---

## Q8. "Hot key — one user firing 50 K req/s."

Solutions:
1. **Local pre-aggregation**: per-pod local counter; flush to Redis every 10 ms. Caller absorbs first-wave; eventually deny upstream. ~50× throughput improvement.
2. **Tiered limits**: a fast in-process cap (per-pod) catches obvious abuse before Redis.
3. **Sticky-route the key** to one pod (sharding by key in the load balancer).

The tradeoff is small inaccuracy at the boundary for huge throughput.

---

## Q9. "How do you verify your implementation is correct?"

- **Property-based**: random rates / costs / arrival patterns; assert `count_allowed_in_window <= limit + slack`.
- **Concurrency tests**: 1000 threads firing simultaneously; assert no over-allow.
- **Chaos tests**: kill Redis primary mid-test; assert fail-open.
- **Integration tests**: real Redis container; replay production traces.

---

## Q10. "Tell me about a specific gotcha."

`INCR + EXPIRE` race: client A INCRs to 1, then dies before EXPIRE. Client B sees count=1 and hits limit too soon — wait, that's not a race; the bug is the *missed* EXPIRE means the key never resets and the user is permanently rate-limited until manual cleanup.

Solution: always set `EXPIRE` inside the atomic Lua block.

---

## Q11. "Per-org limit shared across users in that org?"

Add an "org" scope. Org keys are hash-tagged (`{orgId}:limit:org`). User keys are hash-tagged (`{orgId}:limit:user:userid`). They share a shard, so a multi-key Lua script can atomically deduct from both. Or: deduct from org first, then user, with the small unfairness from sequential checks (V1).

---

## Q12. "Configuration hot-reload — if I lower the limit, what happens to in-flight buckets?"

For Token Bucket: the next check sees the new capacity. Currently `tokens = 900`; new capacity = 500. Next check clamps `tokens = min(tokens, 500) = 500`. Subsequent checks deduct normally.

For Fixed/Sliding: the count carries; next window starts under the new limit.

---

## Q13. "Show me how you'd surface limit headers to the API caller."

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 873
X-RateLimit-Reset: <unix-seconds>
X-RateLimit-Scope: user
Retry-After: 12        (only on 429)
```

When multiple scopes apply, expose the **most-restrictive** scope's headers (smallest remaining). Document that callers should treat headers as informational.

---

## Q14. "When would you NOT use a rate limiter at all?"

- Internal services on a private network with bounded fan-out.
- Read-only static asset endpoints behind a CDN (CDN handles it).
- Endpoints with intrinsic backpressure (e.g., long-running RPCs that block — concurrency limits suffice).

---

## Q15. "What's your stance on `setRateLimit(true)` vs `RateLimiter.check(req)`?"

The latter, always. Rate limiter is an *active* guard, not a flag. The check returns a Decision; caller chooses how to handle (return 429, queue, fall back to cache).

---

## Output

```
Drill questions covered:
- Algorithm tradeoffs and choice
- Distributed correctness via Lua
- Fail-open default
- Multi-scope semantics
- Boundary spike problem
- Hot-key mitigations
- Testing strategy
- Common bugs (INCR+EXPIRE race, missing EXPIRE in Lua)
- Per-org shared budgets
- Hot-reload behavior
- Header semantics
```
