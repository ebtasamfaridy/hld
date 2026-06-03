# 07 · Permission System — Class Diagrams

## Core class diagram

```mermaid
classDiagram
    class AuthorizationService {
      <<interface>>
      +can(user, action, resource) boolean
      +decide(user, action, resource) Decision
      +effectivePermissions(user) Set
    }

    class StandardAuthorizationService {
      -store: AuthorizationStore
      -cache: PermissionsCache
      -evaluator: PolicyEvaluator
    }
    AuthorizationService <|.. StandardAuthorizationService

    class PolicyEvaluator {
      +decide(user, action, resource, perms, ancestors) Decision
    }

    class PermissionsCache {
      +effectivePermsFor(userId) Set
      +ancestorsOf(resourceId) List
      +invalidateUser(userId)
      +invalidateResource(resourceId)
    }

    class AuthorizationStore {
      <<interface>>
      +loadEffectivePermsFor(userId) Set
      +loadAncestorsOf(resourceId) List
      +grant / revoke / etc.
    }

    class GrantRule {
      -action
      -resourcePattern
      -decision: ALLOW | DENY
      +matches(action, resourceId) boolean
    }

    class Decision {
      <<enum>>
      ALLOW
      DENY
    }

    class Role {
      -id, name, parentRoleId
    }
    class Permission {
      -id, action, resourceType
    }
    class User {
      -id, tenantId, attributes
    }
    class Resource {
      -id, type, parentId
    }

    StandardAuthorizationService o-- AuthorizationStore
    StandardAuthorizationService o-- PermissionsCache
    StandardAuthorizationService o-- PolicyEvaluator
    PolicyEvaluator ..> GrantRule
```

## Package layout (`com.authz`)

```
api/      AuthorizationService, Decision, Permission, GrantRule
core/     StandardAuthorizationService, PolicyEvaluator
model/    User, Role, Permission, Resource (POJOs)
policy/   Wildcard matcher, expression evaluator (ABAC stub)
store/    AuthorizationStore, InMemoryAuthorizationStore (+ Postgres stub)
engine/   PermissionsCache (in-memory; Redis-backed in V2)
```

## Why these abstractions

### `AuthorizationService` interface
The PEP calls this. Multiple implementations: in-process, gRPC remote, mock for tests.

### `PolicyEvaluator` is pure
Given `(user, action, resource, perms, ancestors)`, return decision. No side effects. Trivial to unit-test.

### `AuthorizationStore` for storage
In-memory for tests; Postgres for production. Same interface.

### `PermissionsCache` separately
Decoupled from store; can swap to Redis. The cache is the consistency boundary; invalidation is a first-class operation.

### `GrantRule` value object
`(action, resourcePattern, decision)`. Immutable. Drives matching.

### Light ABAC
The skeleton has a placeholder for an attribute-based predicate that can wrap a `GrantRule`. Production extends this.

## Output

```
Layered:     api → core → model + policy + store + engine
Strategy:    AuthorizationStore (in-memory / Postgres)
Pure:        PolicyEvaluator is a deterministic function over inputs
Cache:       PermissionsCache — first-class; invalidation events drive freshness
```
