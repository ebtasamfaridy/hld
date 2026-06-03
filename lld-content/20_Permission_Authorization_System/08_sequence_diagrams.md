# 08 · Permission System — Sequence Diagrams

## 1. `can(user, read, doc:42)` — happy path with cache hit

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant AS as AuthzService
    participant Cache
    participant Eval as PolicyEvaluator

    App->>AS: can(alice, read, doc:42)
    AS->>Cache: effectivePermsFor(alice)
    Cache-->>AS: {(read, doc:*, ALLOW), ...}
    AS->>Cache: ancestorsOf(doc:42)
    Cache-->>AS: [folder:5, project:1]
    AS->>Eval: decide(read, doc:42, perms, ancestors)
    Eval->>Eval: scan rules
    Eval->>Eval: match (read, doc:*) → ALLOW, no DENY found
    Eval-->>AS: ALLOW
    AS-->>App: true
```

## 2. Cache miss — load from DB

```mermaid
sequenceDiagram
    autonumber
    participant AS as AuthzService
    participant Cache
    participant Store as Postgres
    participant Eval

    AS->>Cache: effectivePermsFor(alice)
    Cache-->>AS: null
    AS->>Store: SELECT (recursive CTE)
    Store-->>AS: rows
    AS->>Cache: SET userPerms:alice TTL=5min
    AS->>Eval: decide(...)
    Eval-->>AS: decision
```

## 3. DENY at parent overrides ALLOW at child

```mermaid
sequenceDiagram
    autonumber
    participant Eval

    Note over Eval: perms = [(read, doc:42, ALLOW), (read, folder:5/*, DENY)]<br/>resource path = [doc:42, folder:5, project:1]
    Eval->>Eval: scan doc:42 → (read, doc:42, ALLOW) → flag ALLOW
    Eval->>Eval: scan folder:5 → (read, folder:5/*, DENY) → DENY (stop)
    Eval-->>Eval: DENY
```

## 4. Grant → invalidate cache → next check sees update

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin
    participant PAP
    participant DB
    participant Bus
    participant Cache as Redis (instance 1)
    participant Cache2 as Redis (instance N)

    Adm->>PAP: grant alice the editor role
    PAP->>DB: INSERT user_roles
    PAP->>Bus: publish UserPermsChanged{ alice }
    Bus-->>Cache: invalidate userPerms:alice
    Bus-->>Cache2: invalidate userPerms:alice
    Note over Adm: ~100ms later
    PAP-->>Adm: 200 OK
    Note over Adm: next check for alice loads fresh perms
```

## 5. Inverse query — "who can read doc:42?"

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin UI
    participant AS as AuthzService
    participant View as MaterializedView (Postgres)

    Adm->>AS: grantees(read, doc:42)
    AS->>View: SELECT user_id WHERE resource=doc:42 AND action=read AND decision=ALLOW
    View-->>AS: [u-1, u-2, u-3]
    AS->>AS: subtract DENY-listed users
    AS-->>Adm: filtered list
```

The materialized view is updated by triggers on grant changes (or async via pub/sub).

## 6. Role hierarchy traversal

```mermaid
sequenceDiagram
    autonumber
    participant DB

    Note over DB: alice has role 'editor', editor → viewer
    DB->>DB: WITH RECURSIVE walk
    DB-->>DB: roles = {editor, viewer}
    DB->>DB: SELECT permissions for both
    DB-->>DB: rows: editor.write:doc:* + viewer.read:doc:*
```

## 7. Resource hierarchy walk during decision

```mermaid
sequenceDiagram
    autonumber
    participant Eval

    Note over Eval: resource = doc:42, parent = folder:5, grandparent = project:1
    loop leaf to root
      Eval->>Eval: any DENY rule matching (action, current)? → if yes, DENY (stop)
      Eval->>Eval: any ALLOW rule matching? → flag (don't stop, keep walking for DENYs)
    end
    Eval->>Eval: if any ALLOW seen and no DENY → ALLOW, else DENY
```

## 8. Audit on grant

```mermaid
sequenceDiagram
    autonumber
    participant Adm
    participant PAP
    participant DB
    participant Aud as Audit

    Adm->>PAP: grant alice the editor role
    PAP->>DB: BEGIN
    PAP->>DB: INSERT user_roles
    PAP->>Aud: INSERT audit (actor=adm, action=role_assigned, before=null, after={alice→editor})
    PAP->>DB: COMMIT
```

## Output

```
Hot path:    cache → evaluator → decision; <1ms with cache hit
DENY wins:   evaluator scans all matching rules; can't return on first ALLOW
Cache:       invalidation via pub/sub on every grant change
Inverse:     materialized view; updated async
Hierarchy:   role + resource hierarchies traversed in evaluator
Audit:       transactional with grants
```
