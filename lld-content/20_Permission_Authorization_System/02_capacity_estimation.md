# 02 · Permission System — Capacity Estimation

## Scale (mid-size SaaS)

```
Tenants:                    10 K
Users:                      10 M
Roles:                      100 per tenant (avg)
Permissions:                500 distinct (action × resource type)
Role assignments:           100 M (10 M users × 10 roles avg)
Permission grants per role: 50 (avg)
Total grant rows:           ~5 B (denormalized) or ~5 M (joined)
Resources:                  500 M (docs, folders, projects)
Resource hierarchy depth:   5 (org > workspace > folder > sub > doc)

Check requests / sec:       100 K
Grant writes / sec:         10
```

## Hot path: check

```
can(user, action, resource):
    fetch user's effective permissions (cached)
    walk resource hierarchy bottom-up
    for each level:
        if a DENY rule matches → DENY (stop)
        if an ALLOW rule matches → flag and continue (keep walking for DENYs)
    if any ALLOW found and no DENY → ALLOW
    else DENY (default)
```

Costs:
- Effective permissions cached per user: ~5 KB. 10 M users × 5 KB = 50 GB. Doesn't fit in RAM globally; must be a cache (Redis) with TTL.
- Resource hierarchy walk: 5 levels max. Each level a hash lookup → O(5) work. Negligible.
- Total: < 1 ms with caches; ~5 ms with cold cache (Postgres lookup).

## Cache strategy

```
Per-user cache:
  key:   userPerms:{userId}
  value: { perms: [...], denies: [...], roles: [...] }
  TTL:   5 min
  invalidation: on grant change, publish event; subscribers evict
```

```
Per-resource hierarchy cache (rarely changes):
  key:   resourceAncestors:{resourceId}
  value: [parentId, grandparentId, ...]
  TTL:   1 hr (resources rarely move)
```

## Storage

```
roles:               100 K rows × 1 KB = 100 MB
permissions:         500 rows × 200 B = 100 KB (small, global)
role_permissions:    5 M rows × 200 B = 1 GB
user_roles:          100 M rows × 100 B = 10 GB
resource_grants:     500 M rows × 200 B = 100 GB (direct grants on resources)
audit:               ~1 TB / year (compressible)
```

Postgres can handle this with indexing and partitioning by tenant.

## Inverse queries

"List users with action X on resource Y" — important for admin UIs ("who can read this?").

This is expensive: must check every user. Two approaches:
1. Iterate users in tenant; for each, run `can()`. OK for small tenants.
2. Maintain a "subject lookup" table: resource → users with each action. Updated on grant changes.

Zanzibar's `expand` operation is the formal version of (2).

## What forces design

1. **Sub-ms checks** → cache effective permissions per user.
2. **Cache invalidation** → pub/sub on grant changes.
3. **DENY > ALLOW** → bidirectional pass through grants; can't return on first ALLOW.
4. **Resource hierarchy** → walk parents; cache ancestor lookups.
5. **Inverse queries** → materialized views.
6. **Audit** → every grant change persisted; high write rate to audit table → partition.

## Output

```
Throughput:  100K check/sec; 10 grants/sec
Cache:       per-user effective permissions (Redis); 5min TTL; invalidate on change
Storage:     Postgres with partitioning per tenant; ~100GB grants; ~1TB audit/year
Hot path:    cached lookup + 5-level hierarchy walk → <1ms
Inverse:     materialized lookup table; Zanzibar 'expand'
```
