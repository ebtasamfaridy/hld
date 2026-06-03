# 14 · Feature Flag System — Interviewer Follow-ups

## Q1. "How do you guarantee sub-millisecond evaluation?"

The SDK evaluates **locally**. The server pushes flag config to the SDK; the SDK keeps an in-memory `ConcurrentHashMap<String, Flag>` and runs a pure function `(flag, context) → variation`. No network, no DB; just hash + comparisons.

The server-side eval API (`/evaluate`) exists for thin clients (browsers) but the canonical answer is local SDKs.

---

## Q2. "Walk me through the percentage rollout algorithm. Why is it stable?"

```
salt = flag.key
bucket = hash(salt + ":" + userId) mod 10000   // 0..9999
include = bucket < (percentage * 100)
```

Stable because: (a) input determines output (same userId → same bucket every call); (b) bucket is deterministic across servers; (c) salt is the flag key, so two flags don't accidentally bucket the same users together.

Subset-preserving: at 10%, included users have bucket < 1000. At 50%, included users have bucket < 5000. The 10% set is a subset of the 50% set. **Expansion never removes a user**; shrinkage only removes the highest-bucket users.

---

## Q3. "Why salt with the flag key?"

Without it, hashing `userId` alone would bucket the same 10% of users into every flag. If three independent 10% rollouts always pulled the same 10% of users, those users would experience a correlated treatment across features — bad for analytics, possibly biased.

Salting with `flagKey` decorrelates: the same user is in different buckets across different flags.

---

## Q4. "How do you prevent two admins from clobbering each other's edits?"

Optimistic concurrency:
- Every flag has a `version` integer.
- PUT requires `If-Match: <version>`.
- DB does `UPDATE … WHERE version = $expected RETURNING ...`.
- If 0 rows updated → 409 Conflict; admin sees the latest version, re-edits, retries.

This is the right tradeoff: edits are rare, conflicts are rarer, lock-free protocol is simple.

---

## Q5. "An admin flips a flag in prod. How long until all 500 K servers see it?"

Target: < 2 seconds p95.

Path:
1. Admin API writes Postgres + audit (~10 ms).
2. Publishes to Kafka topic `flag.updates` (~10 ms).
3. Edge SSE servers consume and broadcast to their connected SDKs (~100 ms).
4. SDKs apply the patch atomically (~1 ms).

Total: ~120 ms p50. p95 latency comes from network jitter; <2 s with reasonable infra.

---

## Q6. "What happens if the flag service is completely down?"

SDKs continue to evaluate against their cached config. They return cached values. New flags created during the outage aren't visible, but existing flags work normally. The product never becomes unresponsive due to a flag-service outage.

This is **the** value proposition of feature flags. Never make the product depend on an external service for its core code paths.

---

## Q7. "How do you handle prerequisite cycles?"

At write time, build the dependency graph and run DFS to detect cycles. Reject with 422 if a cycle exists.

In the SDK, evaluation iterates the prereq list once per flag and recursively evaluates each. If somehow a cycle existed (config bug), we cap recursion depth and return the off variation with reason `PREREQUISITE_FAILED`.

---

## Q8. "User reports a bug: flag X is on for them, but they shouldn't be in the rollout. How do you debug?"

`EvaluationResult` includes a `reason` field. Have the user run the SDK in diagnostic mode; the reason tells you exactly:
- `RULE_MATCH(r-pro)` → some rule matched on their context. Inspect their context attributes.
- `FALLTHROUGH` → no rule matched; they got the default.
- `OFF` → kill switch is off.
- `PREREQUISITE_FAILED` → upstream flag denied them.

Audit log shows who flipped what when. Combination of `reason` + audit pinpoints the cause within minutes.

---

## Q9. "How do you A/B test with this system?"

Multi-variate flag with 2+ variations: `control`, `treatment-A`, `treatment-B`. Each variation gets a percentage.

```
rules:
  - conditions: [country IN [IN]]
    kind: PERCENTAGE_BUCKETS,
    buckets: [{variation: 'control', pct: 50}, {variation: 'treatment-A', pct: 25}, {variation: 'treatment-B', pct: 25}]
```

The SDK reports each evaluation back (sampled) to an analytics system. Combined with conversion data, you compute statistical significance offline.

---

## Q10. "Two SDKs in different processes get the same user's request — do they agree on the variation?"

Yes, because:
1. Both SDKs have the same flag config (eventually consistent within the propagation latency).
2. Bucketing is deterministic (hash + flag.key + userId).
3. Rule order is stable (server publishes ordered list).

So same user → same variation across all servers, until config changes. After a config change, there's a brief window where SDKs that have applied the new version differ from SDKs that haven't. The window is the propagation latency (~2 s).

---

## Q11. "What's the audit log used for?"

Three things:
1. **Compliance**: SOX / regulatory audits demand "who changed what when".
2. **Debugging**: bug shows up after someone flipped a flag — find them.
3. **Rollback**: revert to a previous version cleanly.

Audit log is append-only, partitioned by month, retained for years.

---

## Q12. "If I want to add a new operator (say `WITHIN_RADIUS_KM`), how does it propagate?"

Two-step:
1. **SDK update**: every SDK language must learn the operator before any rule using it can evaluate. Without this, an SDK encountering an unknown operator would treat the rule as no-match (safe default).
2. **Server update**: validation logic must accept the new operator.

Coordinate releases: rev SDKs first (clients fetch new versions); then enable the operator server-side.

---

## Q13. "Anonymous users — how do you bucket them?"

Three options:
1. **Session ID**: stable for the session; deterministic during a visit.
2. **Random per call**: not sticky; user might see UI flip.
3. **Cookie / device fingerprint**: stable across sessions but identifying.

Pick session ID for typical UX. Configurable per flag.

---

## Q14. "What if a regex in a rule is malicious (catastrophic backtracking)?"

Pre-compile and run with a timeout at evaluation. Or, restrict to a non-backtracking regex engine (RE2). At write time, validate the pattern with a timeout — reject if it takes > 50ms to test against a sample.

---

## Q15. "Edge case: SDK starts up with no network. What does it do?"

Three layers:
1. Try CDN snapshot.
2. If CDN fails, try local on-disk snapshot from last successful run.
3. If both fail, use the **defaults** the application code passes on every call.

Step 3 is critical: every SDK API takes a `defaultValue`. Even with no flag service ever, the application works.

---

## Output

```
Drilled:
- Local SDK eval is the latency answer
- Sticky bucket: hash(flag.key + userId) mod 10000
- Salt = flag.key for cross-flag decorrelation
- Optimistic concurrency on admin writes
- 2s propagation: API → Kafka → Edge → SDK
- Service outage: SDK works on cached config
- Cycle detection at write time
- EvaluationResult.reason for diagnostics
- Multi-variate for A/B/n
- Cross-server agreement (deterministic eval)
- Audit log for compliance + debugging + rollback
- New operators require SDK rev first
- Anonymous users via session ID
- Regex DoS mitigation (timeout / RE2)
- No-network startup: CDN → disk → application defaults
```
