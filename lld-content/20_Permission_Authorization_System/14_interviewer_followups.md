# 14 · Permission System — Interviewer Follow-ups

## Q1. "Walk me through `can(user, action, resource)`."

1. Look up the user's effective permissions (cache → DB on miss). This includes:
   - Permissions from all roles the user has, including ancestors via role hierarchy.
   - Direct resource grants on the user.
2. Compute the resource's path: leaf → … → root (via resource hierarchy).
3. Walk each level in the path. At each:
   - If a DENY rule matches → return DENY immediately.
   - If an ALLOW rule matches → flag and continue (we still need to scan for DENYs).
4. After exhausting the path: if any ALLOW was seen and no DENY → ALLOW. Else → DENY (default deny).

---

## Q2. "Why DENY > ALLOW?"

So that **blocking always works**. If an admin says "Bob cannot read this folder," that should be effective regardless of any inherited ALLOW Bob might have. It also makes the system **safer to administer**: you can be permissive in the base layer and add specific DENYs without auditing every existing ALLOW.

---

## Q3. "What's the difference between RBAC, ABAC, and ReBAC?"

- **RBAC**: roles enumerate permissions. Simple; brittle when permissions depend on data attributes.
- **ABAC**: policies are predicates over attributes (`user.dept == doc.dept`). Flexible; harder to reason about and test.
- **ReBAC**: relationships in a graph drive access (`user owns doc`). Best for "share with this person" UX (Google Drive, GitHub).

Real systems combine all three. RBAC is the foundation; ABAC adds tenant/sensitivity constraints; ReBAC adds sharing.

---

## Q4. "How do hierarchical roles work?"

Each role has an optional `parentRoleId`. To compute effective permissions for a role, recursively union with the parent.

```
admin → editor → viewer
admin perms = admin's own + editor's + viewer's
```

Cycles forbidden — detect at write time.

---

## Q5. "What happens with a deny in the role hierarchy and an allow at the user level?"

Both are part of the user's effective permission set. Evaluator scans all of them; DENY wins.

So if `admin` (inherited) ALLOWs `read:doc:*` but a direct grant on the user DENYs `read:doc:42`, the user is DENIED on doc:42.

---

## Q6. "How do you make checks fast?"

Cache the user's effective permission set in Redis (or local memory). Cache the resource's ancestor list. Both have long TTLs (5 min for perms, 1 hr for ancestors).

The hot path is then: cache lookup + iterate the resource path checking against the cached set. Sub-millisecond.

---

## Q7. "How do you keep the cache fresh?"

On every grant change (admin operation), publish an `UserPermsChanged{userId}` event to a Bus. All cache instances subscribe and evict.

For role-permission changes affecting many users, evict all entries via a generation number scheme: each cache entry tagged with a generation; admin change increments the generation; entries with older generation are stale.

---

## Q8. "What if the cache is down?"

Fall through to the DB. Latency degrades; correctness preserved. Alert.

---

## Q9. "Walk me through what happens when an admin revokes Bob's editor role."

1. PAP receives the revoke.
2. Postgres: `DELETE FROM user_roles WHERE user_id=bob AND role_id=editor`.
3. INSERT audit row (actor, action=revoked, before, after).
4. Publish `UserPermsChanged{bob}`.
5. Subscribers evict `userPerms:bob` from local caches.

Within ~100 ms, Bob's checks return updated decisions. The lag is the propagation delay.

For sensitive operations (financial, security), wait for confirmed eviction across all instances before returning success to the admin (e.g., via ack from each subscriber). Most apps don't need this strictness.

---

## Q10. "How do you handle multi-tenancy?"

Every table has a `tenant_id`. Every query must filter on it. The application enforces it; for defense in depth, use Postgres row-level security.

A user belongs to exactly one tenant (or N tenants for cross-tenant accounts). Resources are scoped per tenant. Cross-tenant access is forbidden by query construction.

The biggest risk is a bug that omits `tenant_id`. Code review and tests catch this; RLS makes it impossible.

---

## Q11. "What about wildcards? `read:doc:*` vs `read:doc:42` — which wins?"

In a DENY-wins model, neither "wins" in the way you might think. Both are evaluated:
- If both match, both are considered.
- If `read:doc:*` is DENY and `read:doc:42` is ALLOW, DENY wins.
- If `read:doc:*` is ALLOW and `read:doc:42` is ALLOW, the user is ALLOWED (with no DENYs to override).

Some systems do "most-specific ALLOW wins" but only among ALLOWs. The DENY override is universal.

---

## Q12. "How do you support the inverse query: 'who can read this doc?'"

Maintain a materialized view: `subject_resource_action_decision`. Updated by triggers on grant changes (or async via Bus).

Query: `SELECT user_id FROM view WHERE resource=$ AND action=$ AND decision=ALLOW EXCEPT … (subtract DENY users)`.

Zanzibar formalizes this as an `expand` operation that walks the graph backwards.

---

## Q13. "Default deny — why?"

Security principle: **fail closed**. If a check has no matching rule, the safe default is to deny. The alternative — default allow — leads to silent over-permissioning when configs are incomplete.

Production systems with default deny may have annoying "Why can't I…?" tickets, but those are vastly preferable to silent data exfiltration.

---

## Q14. "How do you test this system?"

- **Unit**: PolicyEvaluator with hand-crafted inputs and expected decisions. Property-based tests (random rule sets; assert DENY > ALLOW; default deny).
- **Integration**: full DB + cache + invalidation; assert revoke takes effect within N ms.
- **Security tests**: test cases for known vulnerabilities (cross-tenant access, hierarchy cycles, wildcard misuse).
- **Audit verification**: after every operation, audit row exists.

---

## Q15. "Common bug: a user keeps having access after their role was revoked. What's wrong?"

Cache invalidation didn't fire. Diagnosis:
1. Check audit log: was the revoke persisted? If yes, DB is correct.
2. Check Bus: was `UserPermsChanged{user}` published?
3. Check subscribers: did each cache instance receive the event?
4. Check cache: is the entry actually evicted? (TTL might keep stale entries until expiry.)

Mitigation: shorter TTL (e.g., 1 min) so even if invalidation fails, stale data clears quickly. Tradeoff: more DB load.

---

## Output

```
Drilled:
- Decision algorithm (cache → walk hierarchy → DENY-wins → default deny)
- DENY > ALLOW rationale
- RBAC vs ABAC vs ReBAC tradeoffs
- Role hierarchy + cycle detection
- DENY at user level shadowing inherited ALLOW
- Cache for sub-ms checks
- Cache invalidation via pub/sub on every grant change
- Cache outage fallthrough to DB
- Revoke flow + propagation delay
- Multi-tenancy: always filter; consider RLS
- Wildcards + DENY-wins
- Inverse query via materialized view
- Fail-closed (default deny) rationale
- Test strategy (unit + integration + security + audit)
- Common stale-cache bug + mitigation
```
