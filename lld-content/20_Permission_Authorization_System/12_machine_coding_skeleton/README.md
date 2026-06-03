# 12 · Permission System — Machine Coding Skeleton

In-process authorization with RBAC + role hierarchy + resource hierarchy + DENY-wins evaluator + per-user cache.

```
src/main/java/com/authz/
├── api/         AuthorizationService, Decision, Permission, GrantRule
├── core/        StandardAuthorizationService, PolicyEvaluator
├── model/       User, Role, Permission, Resource (POJOs)
├── policy/      WildcardMatcher
├── store/       AuthorizationStore (interface), InMemoryAuthorizationStore
├── engine/      PermissionsCache (in-memory)
└── Main.java
```

## Demo
1. Define `viewer < editor < admin` hierarchy.
2. Assign `viewer.read:doc:*` and `editor.write:doc:*`. `admin` inherits both.
3. Resource hierarchy: `project:1 > folder:5 > doc:42`.
4. Grants:
   - `editor.write:doc:*` ALLOW on the `editor` role.
   - Direct DENY on `bob` for `read:folder:5/*`.
5. Show:
   - `alice (admin)` can read & write doc:42.
   - `bob (editor)` is DENIED on doc:42 because of folder:5 DENY.
   - `carl (viewer)` can read but not write.
   - Default DENY on actions not granted.
   - Cache invalidation when alice's role changes.
