# 06 · Permission System — API Design

## Check API (the hot path)

```
POST /v1/check
{
  "user": "u-123",
  "action": "read",
  "resource": "doc:42",
  "context": { "ip": "10.0.0.1", "time": "..." }
}
→ 200 { "decision": "ALLOW" | "DENY", "reason": "...optional..." }
```

Or as a library call:

```java
boolean ok = authz.can(user, "read", "doc:42");
Decision d  = authz.decide(user, "read", "doc:42");   // includes reason
```

`decide` includes reason for diagnostics; `can` is the boolean shortcut.

## Batch check (efficiency)

```
POST /v1/check/batch
{ "user": "u-123", "items": [ ["read", "doc:42"], ["write", "doc:42"], ... ] }
→ 200 { "decisions": [ {"action":"read","resource":"doc:42","decision":"ALLOW"}, ... ] }
```

Useful for UI: render an entire page; show/hide buttons based on permissions. One round-trip instead of N.

## List API

```
GET /v1/users/{userId}/effective-permissions
→ 200 { "perms": [ { "action": "read", "pattern": "doc:*", "decision": "ALLOW" }, ... ] }
```

## Inverse query — "who can do X on Y?"

```
GET /v1/resources/{id}/grantees?action=read
→ 200 { "users": [ "u-1", "u-2", ... ] }
```

Expensive. Materialized view. Used in admin UI ("share dialog" listing existing access).

## Admin API

### Roles
```
POST   /admin/roles                          { tenantId, name, parentRoleId? }
GET    /admin/roles
PATCH  /admin/roles/{id}                     { name?, parentRoleId? }
DELETE /admin/roles/{id}
```

### Role-permission grants
```
POST   /admin/roles/{role}/permissions       { permission: "read", resourceType: "doc", pattern: "doc:42", decision: "ALLOW" }
DELETE /admin/roles/{role}/permissions/{id}
GET    /admin/roles/{role}/permissions
```

### User-role assignments
```
POST   /admin/users/{user}/roles             { roleId }
DELETE /admin/users/{user}/roles/{roleId}
GET    /admin/users/{user}/roles
```

### Direct resource grants
```
POST   /admin/resources/{id}/grants          { userId, action, decision }
DELETE /admin/resources/{id}/grants/{grantId}
```

## Resource hierarchy

```
POST   /admin/resources                      { id, type, parentId, attributes }
PATCH  /admin/resources/{id}                 { parentId? }
DELETE /admin/resources/{id}
```

## Audit

```
GET    /admin/audit?actorId=...&target=...&from=...&to=...
GET    /admin/users/{user}/audit
```

## Errors

| Code | Meaning |
| --- | --- |
| 400 | Invalid input |
| 403 | Caller lacks permission to administer (recursive!) |
| 404 | Subject / role / resource not found |
| 409 | Cycle in role hierarchy |
| 422 | Permission references missing role/resource |

## Decision response (for `decide`)

```json
{
  "decision": "ALLOW",
  "reason": "matched role 'editor' with permission 'write:doc:*'",
  "matchedGrants": [
    { "type": "role", "roleId": "...", "permission": "write:doc:*", "decision": "ALLOW" }
  ]
}
```

For diagnostics. Optional; not returned in fast-path.

## Output

```
Hot path:    POST /check or library can(); <1ms p99 with cache
Batch:       POST /check/batch reduces UI round-trips
Inverse:     /resources/{id}/grantees — materialized
Admin:       CRUD on roles, role-permissions, user-roles, resource grants, hierarchy
Audit:       queryable; append-only
Diagnostics: decide() returns reason + matched grants
```
