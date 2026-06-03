# 13 · Permission System — Extensions & Tradeoffs

## Extensions

### 1. ABAC: attribute predicates
A `GrantRule` gains an optional `condition` (e.g., `user.dept == resource.dept`). Rules evaluated as predicates. Engines: OPA / Cedar.

### 2. ReBAC: relationship tuples (Zanzibar)
Schema:
```
user → owner → doc:42
group → member → user
folder:5 → parent → doc:42
folder:5 → viewer → group:eng
```

Check `read:doc:42 by user`: walk graph: is user a member of group:eng? Is folder:5 a parent of doc:42? Is group:eng a viewer of folder:5? If yes → ALLOW.

Implemented via specialized graph stores (SpiceDB) or ad-hoc in Postgres for small scale.

### 3. Time-bound grants
`expires_at`. Filter expired in queries. Background cleanup deletes old.

### 4. Approval workflow
Some grants require review. Pending grant table; once approved, moved to active.

### 5. Field-level permissions
Permission per attribute: `read:user.email` vs `read:user.salary`. Pattern carries `field`.

### 6. Tenant-scoped roles vs global roles
Some roles (`platformAdmin`) are global; most are tenant-scoped. Schema has both.

### 7. Cache + materialized view for inverse queries
`subject_perms_view` updated by triggers; used for "who has access to X."

### 8. SCIM / LDAP integration
Sync users + groups from external identity providers.

### 9. Just-in-time (JIT) elevation
"Sudo mode" — temporary admin privileges with re-auth + audit + auto-expiry.

### 10. Decision logs
Log every check decision (sampled) for security analytics.

## Tradeoffs

### RBAC vs ABAC vs ReBAC

| RBAC | ABAC | ReBAC |
| --- | --- | --- |
| Simple; well-understood | Flexible; expressive | Powerful for sharing primitives |
| Roles must enumerate every case | Policy expressions can be complex | Needs graph store |
| **Pick**: RBAC for 90% of apps; add ABAC if you have data-sensitivity constraints; add ReBAC if you need Google-Drive-style sharing |

### DENY > ALLOW

Pros: safe by default; admins can override accidentally permissive rules.
Cons: harder to reason about; a stray DENY can lock out an entire team.

Always audit DENY rules. Provide UI: "this user is denied because of rule X on folder Y."

### Wildcard specificity rules

Some systems: most-specific ALLOW wins; DENY wins regardless. Others: DENY wins; ALLOW only if no DENY anywhere.

Pick the simpler "DENY wins; default deny" semantics. Document clearly.

### Cache TTL vs invalidation

| Short TTL (1m) | Long TTL (1h) + invalidation |
| --- | --- |
| No infra; eventual freshness | Tight freshness; needs Bus |
| Higher DB load on TTL expiry storms | Lower steady-state load |
| **Pick**: long TTL + invalidation for security-sensitive systems |

### Materialized inverse view

Keeps "who has access to X" cheap. Cost: write amplification on grant changes. For most apps, write rate is low (10/sec) — totally fine.

### Resource hierarchy depth

Deep hierarchies (50 levels) blow up the walk. Cap at e.g. 10. Reject deeper hierarchies.

### Multi-tenancy boundary

Always include `tenant_id`. Defense in depth: enforce in app + Postgres RLS.

### Negative permissions in role hierarchy

`viewer → editor`: editor inherits viewer's grants. What if editor has a DENY for something viewer ALLOWs?

Two interpretations:
- **DENY shadows inherit**: editor's DENY blocks viewer's ALLOW. Confusing if not careful.
- **Inheritance is union of grants**: DENYs and ALLOWs both inherited; final decision still DENY-wins.

Pick the second. It's predictable.

## Open questions

- Should we permit ALLOW + DENY on the same role? (Yes, with clear semantics.)
- How do we report decisions in admin UI? (Decision + matched rules, like the `decide()` API.)
- Do we want resource-attribute-based permissions (e.g., `read if doc.owner == user.id`)? (Yes — that's ABAC.)
- Should we cache decisions? (No — cache the inputs, not the answer; resource pattern matching is cheap once perms are loaded.)

## Output

```
Extensions:    ABAC, ReBAC (Zanzibar), time-bound grants, approval workflows,
               field-level perms, tenant-scoped roles, materialized inverse view,
               SCIM sync, JIT elevation, decision logs
Tradeoffs:     RBAC vs ABAC vs ReBAC; DENY-wins semantics; wildcard specificity;
               cache TTL vs invalidation; hierarchy depth caps; multi-tenancy
Pre-decided:   RBAC + role hierarchy + resource hierarchy; DENY > ALLOW;
               default deny; cache effective perms per user with pub/sub invalidation;
               PEP/PDP/PAP/PIP separation
Open Qs:       allow+deny same role; admin reporting; ABAC feature scope; decision caching
```
