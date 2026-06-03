# 03 · Permission System — High-Level Design

## Architecture

```mermaid
flowchart LR
    Client -- request --> App[Application]
    App -->|"check(user, action, resource)"| PEP[Policy Enforcement Point]
    PEP -- query --> PDP[Policy Decision Point]
    PDP -- read --> Cache[(Redis cache:<br/>effective perms)]
    Cache -. miss .-> DB[(Postgres:<br/>roles, perms, grants, hierarchy)]
    PDP -- attributes --> PIP[Policy Information Point]
    PIP -. fetch user/resource attrs .-> DB

    Admin[Admin UI] -- grant/revoke --> PAP[Policy Administration Point]
    PAP -- INSERT/UPDATE --> DB
    PAP -- INSERT --> Audit[(Audit log)]
    PAP -- publish --> Bus[Bus / Pub-Sub]
    Bus --> Cache
```

## Roles (authz industry vocab)

| Role | Responsibility |
| --- | --- |
| **PEP** (Enforcement) | Calls `check()` at every protected entry point. Enforces decision. |
| **PDP** (Decision) | Evaluates policies; returns ALLOW/DENY |
| **PAP** (Administration) | Manages policies (admin UI / API) |
| **PIP** (Information) | Provides attributes (user dept, resource owner) |

Separating PEP from PDP makes the system testable: the PDP is a pure function of policies + attributes.

## Hot path: `can(user, action, resource)`

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant PEP
    participant PDP
    participant Cache as Redis
    participant DB as Postgres

    App->>PEP: can(alice, read, doc:42)
    PEP->>PDP: decide(alice, read, doc:42)
    PDP->>Cache: get userPerms:alice
    alt cache hit
      Cache-->>PDP: {perms, denies, roles}
    else cache miss
      PDP->>DB: SELECT effective perms
      DB-->>PDP: rows
      PDP->>Cache: SET userPerms:alice ttl=5m
    end
    PDP->>Cache: get ancestors:doc:42
    Cache-->>PDP: [folder:5, project:1]
    PDP->>PDP: walk [doc:42, folder:5, project:1]
    Note over PDP: at each level — any DENY? → DENY<br/>any ALLOW? → flag, keep walking for DENYs
    PDP-->>PEP: ALLOW
    PEP-->>App: true
```

## Decision algorithm (RBAC + hierarchy + DENY)

```python
def can(user, action, resource):
    perms = get_effective_perms(user)  # cached: {(action, resourcePattern, ALLOW|DENY)}
    saw_allow = False
    for r in resource_path(resource):  # leaf → root
        for (act, pattern, decision) in perms:
            if matches(act, pattern, action, r):
                if decision == DENY:
                    return DENY
                else:
                    saw_allow = True
        # important: continue scanning parents for any DENY
    return ALLOW if saw_allow else DENY
```

**DENY wins**, regardless of position. **Default deny** if no rule matches.

## Grant write path

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin
    participant PAP
    participant DB
    participant Aud as Audit
    participant Bus
    participant Cache

    Adm->>PAP: PUT user_roles { user, role }
    PAP->>DB: INSERT user_roles
    PAP->>Aud: INSERT audit row (actor, before, after)
    PAP->>Bus: publish UserPermsChanged{ user }
    PAP-->>Adm: 200 OK
    Bus->>Cache: invalidate userPerms:{user}
```

The cache invalidation is the consistency boundary. Within a few hundred ms, all PDP instances get the update via pub/sub.

## Wildcard matching

Action patterns: `read`, `write`, `*` (any).
Resource patterns: `doc:*`, `doc:42`, `folder:*/doc:*`.

Specificity rule: more specific (fewer `*`) wins for ALLOW. DENY always wins regardless of specificity.

For interview, simple matching: literal equality + `*`. Full glob/regex is V2.

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Cache outage | Fall through to DB; degrade latency, not correctness |
| DB outage | PDP can serve from cache; new users default deny |
| Bus delay | Stale cache; check returns ALLOW for revoked permission for ~5 min |
| Misconfigured rule | Test in staging; admin-rules audit log |
| Tenant cross-leak bug | Always include `tenant_id` in queries; never query without it |
| Cycle in role hierarchy | Reject at write time |

## Output

```
Roles:    PEP (enforce) | PDP (decide) | PAP (admin) | PIP (attributes)
Hot path: cached effective perms + cached ancestors → walk leaf-to-root
Algo:     DENY wins; default deny; wildcards by specificity for ALLOW
Write:    DB + audit + invalidate via Bus
Failure:  cache fallthrough; tenant scoping enforced everywhere
```
