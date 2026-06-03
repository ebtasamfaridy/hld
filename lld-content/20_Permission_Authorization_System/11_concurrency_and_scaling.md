# 11 · Permission System — Concurrency & Scaling

## Read-heavy by design

100K checks/sec : 10 grants/sec → ratio 10000:1. The system is **massively** read-heavy. Optimize reads.

## Caching strategy

### Cache effective permissions per user
- Key: `userPerms:{tenantId}:{userId}`.
- Value: set of `(action, resourcePattern, decision)`.
- TTL: 5 min.
- Size: ~5 KB per user.
- Storage: Redis (in production); local in-memory for tiny apps.

### Cache resource ancestors
- Key: `ancestors:{resourceId}`.
- Value: list of parent IDs.
- TTL: 1 hr (resources rarely move).

### Cache invalidation
On any grant change:
1. Compute affected users (the user being granted; or all users with the role).
2. Publish `UserPermsChanged{ userId }` to Bus.
3. Subscribers across instances evict their local copies.

For role-permission changes, the affected user set may be large (all users with the role). Instead of evicting per-user, increment a global generation number; cached entries include the gen they were loaded against; on read, check current gen and reload if stale. This avoids the "evict 100k cache entries" stampede.

## Concurrent reads

`PermissionsCache` is a `ConcurrentHashMap<String, EffectivePerms>` (or Redis client). Reads are lock-free.

`EffectivePerms` is **immutable** after load. Multiple threads reading the same set is safe without synchronization.

## Concurrent writes (grants)

Grant writes are rare (10/sec). A simple transactional INSERT/UPDATE on Postgres works without contention.

For optimistic concurrency on role changes (e.g., two admins update the same role's permissions), use `version` column + `If-Match`.

## Decision evaluator concurrency

The evaluator is a pure function. Multiple threads can call `decide()` concurrently with the same inputs and get the same result. No state, no synchronization.

## Cache stampede

On cache eviction (TTL expiry or invalidation), 100 concurrent threads might all call `loadEffectivePerms` simultaneously, hammering the DB.

Mitigations:
- **Single-flight**: the first thread loads; others wait for the result. Implement via `ConcurrentHashMap.computeIfAbsent` with a `CompletableFuture`.
- **Stale-while-revalidate**: serve stale data while a single async load runs.
- **Jitter on TTL**: each entry's TTL is `5min ± random(30s)` so they don't all expire at the same instant.

## Inverse query at scale

"Who can read doc:42?" — naively iterates all users.

Approaches:
1. **Materialized view**: `subject_perms(user_id, action, resource_pattern, decision)` updated by triggers / async on grant changes. Direct query.
2. **Inverted index** keyed by `(action, resource)` → list of users.
3. **Zanzibar's `expand`**: walk the relationship graph in reverse to enumerate users.

For interview, materialized view is the expected answer.

## Permission grant write performance

```
INSERT INTO user_roles (user_id, role_id, tenant_id) VALUES (..., ..., ...);
INSERT INTO permission_audit (...);
PUBLISH UserPermsChanged TO bus;
```

All in a single Postgres transaction; bus publish via outbox or transactional publish.

10/sec is trivial for any DB.

## Multi-tenant isolation

Every query must filter on `tenant_id`. Options:
1. **Application-enforced**: every repository method takes a `TenantContext`; queries always include it.
2. **Postgres RLS** (row-level security): policies on every table; `SET app.tenant_id = '...'` per session; RLS auto-filters.

RLS is safer (defense in depth) but adds operational complexity. Application-enforced + code review is the common answer.

## Hot bottlenecks

| Bottleneck | Mitigation |
| --- | --- |
| Cache eviction → all instances reload simultaneously | Single-flight; jitter TTL |
| Big role assigned to 1M users → invalidate 1M cache entries | Generation number scheme |
| Recursive CTE for role hierarchy at 1000 levels | Cap depth; flatten; reject deep hierarchies |
| Audit table writes | Partition by month; bulk insert |
| Inverse query at scale | Materialized view |

## Failure modes

| Failure | Behavior |
| --- | --- |
| Cache outage | Fall through to DB; latency degrades; checks still work |
| DB outage | Cached entries serve until TTL; new users → DENY (fail closed) |
| Bus outage | Stale cache; checks may ALLOW briefly after revoke; alert |
| Bug in evaluator | Audit checks against expected outcomes; canary test in staging |
| Misconfigured wildcard | Audit log; admin can revert |

## Output

```
Read-heavy:    100K reads/sec; cache effective perms per user (Redis)
Cache:         immutable EffectivePerms; single-flight on miss; jitter TTL;
               generation-number scheme for bulk invalidation
Inverse:       materialized view for "who can do X on Y"
Tenancy:       always filter by tenant_id; consider RLS as defense in depth
Failure:       fail closed (default deny); cache fallthrough on outages
```
