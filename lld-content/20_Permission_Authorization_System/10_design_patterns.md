# 10 · Permission System — Design Patterns

## 1. Strategy — `AuthorizationStore`
In-memory, Postgres, gRPC remote. Same interface.

## 2. Strategy — `PolicyEvaluator`
RBAC-only evaluator vs RBAC + ABAC (with attribute predicates) vs RBAC + ABAC + ReBAC (with graph traversal).

## 3. Repository — `AuthorizationStore`
Persistence abstraction.

## 4. Pure function — `decide(...)`
No side effects. Easy to unit-test exhaustively.

## 5. Composite — Resource hierarchy + Role hierarchy
Both are tree (or DAG) structures evaluated recursively.

## 6. Cache-aside — `PermissionsCache`
Read: cache → load on miss → cache. Write: invalidate. Standard pattern.

## 7. Pub/Sub — Cache invalidation
On grant change, publish event; all instances evict their cache entries.

## 8. Specification pattern — `GrantRule`
Each rule is a self-contained predicate (`matches(action, resource)`). Composable.

## 9. Builder — Permission DSL (V2)
```java
permission("read")
    .on("doc:42")
    .for(role("editor"))
    .allow();
```

## 10. PEP / PDP / PAP / PIP separation (architectural)
A formal authorization-architecture pattern. Each role has clear responsibility.

## 11. Default deny (security pattern)
"Fail closed." If unsure, deny. The framework's default decision is DENY.

## 12. Negative permission (DENY) priority
DENY beats ALLOW regardless of specificity. Encodes the principle "blocking should always work."

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| Per-request DB query | Latency catastrophe; cache + pub-sub invalidation |
| ALLOW-on-first-match short-circuit | Misses DENYs; security hole |
| Default ALLOW | Unsafe — fail closed |
| String concatenation for permission keys without escaping | Injection / collision; use structured types |
| Same cache for permission set + decision | Decisions depend on resource; cache the inputs, not the answer |
| Cross-tenant queries | Critical security boundary; always filter by tenant |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | AuthorizationStore / PolicyEvaluator | Pluggable storage, pluggable policy model |
| Repository | AuthorizationStore | Persistence abstraction |
| Pure function | PolicyEvaluator | Testability; deterministic |
| Composite | Role + Resource hierarchies | Recursive traversal |
| Cache-aside | PermissionsCache | Sub-ms reads with eventual freshness |
| Pub/Sub | Cache invalidation | Cross-instance freshness |
| Specification | GrantRule.matches | Composable predicates |
| Architectural roles | PEP/PDP/PAP/PIP | Separation of concerns |
| Default deny | Throughout | Fail closed |
| DENY > ALLOW | Evaluator | Safe-by-default policy semantics |

## Output

```
The system is RBAC + Resource Hierarchy + Cache-aside, with strict PEP/PDP/PAP
separation, default-deny semantics, and DENY-priority. ABAC and ReBAC layers
plug in as more sophisticated PolicyEvaluator strategies.
```
