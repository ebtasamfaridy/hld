# 01 · Permission System — Requirements

## Functional requirements

### Core (RBAC)
- **Users**, **Roles**, **Permissions** (action + resource type).
- Assign permissions to roles.
- Assign roles to users.
- Hierarchical roles: `admin → editor → viewer`. Higher roles inherit lower roles' permissions.
- Check API: `can(user, action, resource) → boolean`.
- Multi-tenant: assignments scoped per tenant/workspace.
- Group permissions by **resource hierarchy**: e.g., a permission on a folder applies to all files within unless overridden.

### ABAC layer
- Policies as boolean expressions over attributes:
  - User attributes (department, country, manager).
  - Resource attributes (owner, sensitivity, created_at).
  - Environment (time of day, request IP).
- Example: `allow if user.dept == resource.dept`.

### ReBAC layer (V2)
- Relationship tuples: `(user U, relation R, object O)`.
  - `(alice, owner, doc:42)`
  - `(bob, viewer, folder:5)`
- Graph traversal: "alice can edit doc:42 because she is its owner; bob can view doc:42 because doc:42 is in folder:5 and bob is a viewer of folder:5."

### Always
- **DENY rules** override ALLOWs.
- **Audit**: every grant change persisted with actor + timestamp.
- **Inverse queries**: "list users with action X on resource Y" (for admin views).
- **List effective permissions** for a user.

## Out of scope (V2+)
- Distributed Zanzibar-scale consistency (zookies / snapshot tokens).
- Client-side enforcement (we do server-side only).
- OAuth / OIDC issuance (we consume identity from upstream).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Check latency | < 1 ms p99 | On every request |
| Grant write | < 50 ms | Admin operation, less hot |
| Audit completeness | 100 % | Compliance |
| Strong consistency for grant→check | within seconds globally | "I just revoked Bob; he should be blocked." |
| Throughput | 100 K checks/sec | App fleet load |
| Cache freshness | < 30 s | Tradeoff: stale OK; mutating events should invalidate fast |

## Actors

```
User           - authenticated principal
Role           - bag of permissions
Permission     - action + resource pattern
Resource       - thing being protected (doc, folder, project)
PEP            - Policy Enforcement Point: checks at request handler
PDP            - Policy Decision Point: where rules are evaluated
PIP            - Policy Information Point: attribute provider
Admin          - assigns roles / permissions
```

## Edge cases

| Case | Handling |
| --- | --- |
| User has no roles | Default deny |
| Permission has wildcards | `read:doc:*` vs `read:doc:42` — most-specific wins; framework documents the rule |
| Hierarchical role cycle | Reject at write time |
| Resource hierarchy with conflicting perms | DENY wins; closest ancestor's grant wins for ALLOW |
| Removed user still in DB | Mark inactive; checks return DENY |
| Cache staleness during grant change | Invalidate via pub/sub on every grant update |
| DENY on parent + ALLOW on child | DENY wins (typical safe-by-default) |
| Multi-tenant cross-leak | Strict tenant scoping; never query across tenants |
| Listing perms for user with 1000 roles | Materialize to a set; cache |
| Race: revoke while admin grants new role | Last-write wins per row; ordering preserved by audit |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| RBAC with hierarchical roles | ✓ | |
| Resource hierarchy | ✓ | |
| Wildcards in permissions | ✓ | |
| DENY rules | ✓ | |
| Audit log + inverse queries | ✓ | |
| Cached effective set per user | ✓ | |
| ABAC expressions | basic | |
| ReBAC tuples + graph traversal | | ✓ |
| Zanzibar-style consistency tokens | | ✓ |
| Time-bound grants | | ✓ |
| Approval workflows for grants | | ✓ |
| Fine-grained field permissions | | ✓ |

## Output

```
Core:    RBAC with hierarchical roles + resource hierarchy + wildcards + DENY;
         multi-tenant; audit; inverse queries; ABAC expressions
NFR:     <1ms p99 checks; cache effective sets; strong-enough consistency for revokes
Edge:    cycles, wildcard precedence, DENY > ALLOW, cache invalidation,
         tenant isolation, race conditions
```
