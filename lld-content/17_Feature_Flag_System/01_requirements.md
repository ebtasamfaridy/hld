# 01 · Feature Flag System — Requirements

## Functional requirements

### Core
- **Define flags** with key, description, environment, default value.
- **Boolean flags**: on/off.
- **Multivariate flags**: 2+ variations (control / variant-A / variant-B / …).
- **Targeting rules**: a list of conditions; first match wins.
  - Examples: `country in ['IN','US']`, `userId in ['u-1','u-2']`, `email endsWith '@company.com'`, `tier == 'pro'`.
- **Percentage rollouts** with **stable bucketing**: 10 % of users see variant; same user always sees same side.
- **Prerequisite flags**: flag B is evaluable only if flag A is true.
- **Multiple environments** (dev / staging / prod); same flag key, separate config per env.
- **Audit log**: every change persisted with who/what/when.
- **Real-time propagation**: change in admin UI reflects on servers within ~1–2 s.

### Required extensions
- **Scheduled flips** (turn on at a future time; off after a window).
- **Kill switch** mode: a flag flagged as critical can be flipped without going through normal review.
- **Custom attribute targeting** (any field on context, not just predefined).
- **Off-by-default for new flags** (safe default).
- **SDKs for languages** (Java, Go, Python, JS): all share the same evaluation algorithm so server and client agree.

### Out of scope (V2+)
- Experimentation / statistical analysis (separate analytics system).
- Feature dependencies as a DAG with cycle detection (treat as simple prereq for V1).
- Multi-region active-active.

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Evaluation latency | < 1 ms p99 | On hot request path |
| Propagation latency (admin → server) | < 2 s p95 | Don't release a feature unintentionally for long |
| Availability | 99.99 % | A flag service down = feature defaults applied — must be safe |
| Correctness on bucketing | Stable: same user always same bucket | Whole experiments depend on it |
| Audit completeness | 100 % | Compliance |

## Actors

```
Application   - calls SDK.isOn(key, context)
SDK / Client  - in-process evaluator; caches flag config; subscribes to updates
Admin User    - changes flags via web UI
Admin API     - validates + writes; publishes to Bus
Stream/Push   - SSE or websocket per SDK connection
Backing Store - source of truth (Postgres) + cache (Redis)
Auditor       - read-only consumer of audit log
```

## Edge cases

| Case | Handling |
| --- | --- |
| Flag missing | Return supplied default; never throw |
| Service unreachable | SDK uses cached state; default if no cache |
| Two admins edit the same flag concurrently | Optimistic concurrency (`If-Match` header) |
| Percent rollout from 10% to 50% | Same users in 10% bucket are in 50% bucket; bucket is order-stable |
| Percent rollout from 50% back to 10% | Same users dropped; new bucket is a subset of old |
| Targeting rule mistargets a real user | Audit log; rollback by reverting to previous version |
| Prerequisite flag off | Dependent flag also off |
| New flag added | All servers learn within propagation latency |
| Multi-environment leak | Strict environment scoping in store; no cross-env reads |
| User has no userId (anonymous) | Bucketing falls back to a session id or random per-call (configurable) |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Boolean + multivariate flags | ✓ | |
| Targeting rules (eq, in, prefix, suffix, contains) | ✓ | |
| Percentage rollout (sticky) | ✓ | |
| Prerequisite flags | ✓ | |
| Audit log | ✓ | |
| Polling SDK | ✓ | |
| Streaming SDK (SSE) | basic | improved |
| Scheduled flips | | ✓ |
| Multi-environment workspaces | basic | full RBAC |
| A/B experimentation analytics | | external |
| Geo-aware rollout | | ✓ |
| Approval workflow for prod flips | | ✓ |

## Output

```
Core:    define flags; targeting rules; sticky percentage rollouts;
         prerequisite flags; audit log; multi-env; SDK push updates
NFR:     <1ms p99 eval; <2s propagation; 99.99% availability;
         stable bucketing
Edge:    fallback to default on outage; concurrent admin edits;
         rollout up/down preserving subset relationship
```
