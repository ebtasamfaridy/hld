# 13 · Feature Flag System — Extensions & Tradeoffs

## Extensions

### 1. Scheduled flips
Flag config has `enableAt: timestamp`, `disableAt: timestamp`. Server-side scheduler flips and publishes at those times.

### 2. Approval workflow for prod
Edits to prod require N approvals. Implemented as a queue of "pending changes" with reviewers.

### 3. Custom operators
SDK and server share an operator registry. `SEMVER_GT`, `WITHIN_RADIUS`, `IS_PRIME_USER` — plug in by name.

### 4. Multi-variate (4+ variations)
Already supported in the model. Useful for multi-arm experiments.

### 5. Rollout by attribute
Roll out gradually by `country`, `region`, `cohort`, not just `userId`. Same hash-based bucketing, just different salt + key.

### 6. Approval & two-person rule
Some flags (e.g., `kill-switch`) require two distinct admins to approve before flipping.

### 7. Auto-clean stale flags
Detect flags that haven't been evaluated in N days; mark as candidates for archival.

### 8. Webhooks on flag changes
External systems (CI, alerting) get notified. Same Bus, different consumer.

### 9. Local file fallback
SDK persists last known config to disk. On a cold start with no network, evaluate from disk.

### 10. Statistical exposure tracking
SDK reports each evaluation back to the platform (sampled). Powers analytics: "how many users actually saw this flag yesterday?"

## Tradeoffs

### Local SDK eval vs remote eval

| Local SDK | Remote (per-call) |
| --- | --- |
| <1µs latency | 1–5ms latency |
| Cached config can drift | Always-fresh |
| Eval logic must be replicated in every SDK language | Centralized eval logic |
| **Pick**: local eval is the only viable answer at scale |

### Push (SSE) vs Pull (polling)

| Push | Pull |
| --- | --- |
| <2s propagation | 30–60s propagation |
| Long-lived TCP per SDK | Stateless requests |
| Edge infra needed | CDN-cached endpoint enough |
| **Pick**: push for prod; pull as fallback |

### Sticky bucket via hash vs persistent assignment

| Hash bucket | Persistent (DB) |
| --- | --- |
| Stateless; same input → same output | Requires DB lookup; slow |
| Subset-preserving on rollout up | Doesn't preserve unless coded carefully |
| Same hash for two flags can correlate users | Use flag.key as salt to decorrelate |
| **Pick**: hash bucket. Always. |

### Prerequisite chain depth

We allow chains, but cap depth (e.g., 5) to avoid pathological eval cost. Validate at write time.

### Operators: regex / SQL-like / DSL?

| Just primitives (eq, in, prefix) | Full DSL |
| --- | --- |
| Easy to validate, fast | Flexible but complex; injection risk |
| **Pick**: primitive set + REGEX as the escape hatch. Validate timeout on regex compilation. |

### When a flag is "graduated" (always true forever)

A common LD anti-pattern: flags lingering long after rollout. Operationally, mark "graduated" flags and audit. Eventually remove from code; set fallthrough = on.

## Open questions

- Multi-region active-active? (Replicate Postgres + Bus across regions.)
- Per-tenant rate limits on admin writes? (Yes, prevent runaway.)
- SDK metrics back to platform — sampling rate? (1% default; configurable.)
- How aggressively to remove dead flags? (Quarterly review.)
- Schema evolution for variation values (boolean → multivariate)? (Add new variation; deprecate old.)

## Output

```
Extensions:    scheduled flips, approvals, custom operators, multivariate,
               rollout by attribute, two-person rule, auto-clean, webhooks,
               offline fallback, exposure analytics
Tradeoffs:     local vs remote eval; push vs pull; hash bucket vs persistent;
               primitive vs DSL operators
Pre-decided:   local SDK eval; SSE primary + polling fallback; hash bucketing;
               immutable Flag with version-based update; audit-everything
Open Qs:       multi-region, write rate limits, exposure sampling, dead flag policy
```
