# 13 · Rate Limiter — Extensions & Tradeoffs

## Extensions

### 1. Cost-weighted limits
Each request carries a `cost` (1 for cheap, 5 for heavy). All algorithms accept `cost` in `check`. Caller derives cost from endpoint metadata.

### 2. Hierarchical (org → user)
The most-restrictive scope wins (we already do this). For org-budget that's *shared* across users, add an org-level Token Bucket key. Users deduct from both org and personal budgets.

### 3. Adaptive limits
Background process measures backend p95 latency / error rate. When degraded, automatically tighten limits (e.g., halve refill rate). When healthy, relax. The limit config becomes a function of system health.

### 4. Per-route configurations via DB
`LimitConfigProvider` backed by Postgres + cached. Operator updates rates via admin UI; cache invalidation by version bump.

### 5. Bypass tokens (admins, internal services)
A header `X-Internal-Auth: <signed-token>` short-circuits the limiter. Audit every bypass usage.

### 6. Two-phase atomic multi-scope check
A single Lua script that quotes all scopes, decrements only if all pass. Requires hash tags so all keys land on the same shard.

### 7. Long-poll-friendly variant
Some clients hold connections open for minutes. Token-Bucket-with-yield: a request can yield its token if it completes faster than expected. (Niche.)

### 8. Geo-distributed accuracy
Per-region Redis cluster + cross-region async aggregation. Eventual consistency on global limits. Often unnecessary — region-local limits suffice.

### 9. Fingerprint-based (anti-bot)
Compose with a fingerprint signal (browser TLS, user agent shape). Treat suspicious fingerprints with stricter limits.

## Tradeoffs

### Token Bucket vs Sliding Counter

| Criterion | Token Bucket | Sliding Counter |
| --- | --- | --- |
| Bursts | yes (up to capacity) | no (smoothed) |
| State per key | 16 B | 24 B |
| Boundary fairness | smooth | smooth |
| Most APIs | ✓ default | ✓ alternate default |
| Decision | Token Bucket as default; switch to Sliding Counter when bursts must be limited. |

### Sliding Window Log vs Counter

| Criterion | Log | Counter |
| --- | --- | --- |
| Accuracy | exact | ±5 % |
| State | O(limit) | O(1) |
| Memory at high RPS | bad | great |
| Decision | Log only for low-RPS / high-value APIs; Counter elsewhere. |

### Fail-open vs fail-closed

| Criterion | Fail-open | Fail-closed |
| --- | --- | --- |
| Redis outage impact on API | none | API down |
| Abuse window | brief | none |
| Use case | most APIs | billing-grade quotas |
| Decision | **Fail-open** as default. |

### Single-request Lua vs multi-request batched

For very high RPS to a single key (hot key), batched local pre-aggregation pushes to Redis once per N ms. Trade ε ms accuracy for orders-of-magnitude throughput.

### In-process vs Redis

| Criterion | In-process | Redis |
| --- | --- | --- |
| Latency | µs | ms |
| Cross-pod accuracy | no | yes |
| Failure isolation | yes (no dep) | depends on Redis |
| Scope | per-pod CPU defense | global API key |
| Decision | use both: in-process tier-1; Redis tier-2 (when global accuracy matters). |

## Open questions

- How to expose remaining quota for *all* applicable scopes in a 200 OK header? (Common shape: report the most-restrictive scope's remaining only; document so callers know which scope they're up against.)
- Per-method (POST vs GET) limits? (Usually combined with route limits.)
- Whether to count failed requests against the budget? (Default yes; some APIs only count 2xx.)

## Output

```
Extensions:    cost-weighted, hierarchical, adaptive, DB-backed config,
               bypass tokens, two-phase multi-scope, long-poll, geo-distributed
Pre-decided:   Token Bucket default, fail-open, Lua atomic, single-key sharding
Open Qs:       header semantics, per-method limits, counting failed requests
```
