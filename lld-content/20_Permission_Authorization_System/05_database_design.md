# 05 · Permission System — Database Design

## Postgres schema

```sql
CREATE TABLE tenants (
  id   uuid PRIMARY KEY,
  name text NOT NULL UNIQUE
);

CREATE TABLE users (
  id          uuid PRIMARY KEY,
  tenant_id   uuid NOT NULL REFERENCES tenants(id),
  email       text NOT NULL,
  active      boolean NOT NULL DEFAULT true,
  attributes  jsonb NOT NULL DEFAULT '{}',
  UNIQUE (tenant_id, email)
);

CREATE TABLE roles (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL REFERENCES tenants(id),
  name            text NOT NULL,
  parent_role_id  uuid REFERENCES roles(id),
  description     text,
  UNIQUE (tenant_id, name)
);

CREATE TABLE permissions (
  id            uuid PRIMARY KEY,
  action        text NOT NULL,        -- 'read' | 'write' | 'delete' | '*'
  resource_type text NOT NULL,        -- 'doc' | 'folder' | '*'
  description   text,
  UNIQUE (action, resource_type)
);

CREATE TABLE role_permissions (
  id                uuid PRIMARY KEY,
  role_id           uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id     uuid NOT NULL REFERENCES permissions(id),
  resource_pattern  text NOT NULL,        -- 'doc:42' or 'folder:5/*' or '*'
  decision          text NOT NULL CHECK (decision IN ('ALLOW','DENY')),
  granted_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (role_id, permission_id, resource_pattern, decision)
);

CREATE INDEX idx_role_perms_role ON role_permissions(role_id);

CREATE TABLE user_roles (
  user_id   uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id   uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  tenant_id uuid NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- Direct grant on a specific resource (bypassing roles).
CREATE TABLE resource_grants (
  id            uuid PRIMARY KEY,
  user_id       uuid NOT NULL,
  resource_id   uuid NOT NULL,
  action        text NOT NULL,
  decision      text NOT NULL CHECK (decision IN ('ALLOW','DENY')),
  granted_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, resource_id, action, decision)
);

CREATE INDEX idx_grants_user_resource ON resource_grants(user_id, resource_id);

CREATE TABLE resources (
  id           uuid PRIMARY KEY,
  tenant_id    uuid NOT NULL,
  type         text NOT NULL,         -- 'doc' | 'folder' | 'project'
  parent_id    uuid REFERENCES resources(id),
  name         text,
  attributes   jsonb NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_resources_parent ON resources(parent_id);

-- Append-only.
CREATE TABLE permission_audit (
  id        uuid PRIMARY KEY,
  actor_id  uuid NOT NULL,
  action    text NOT NULL,           -- 'role_assigned' / 'role_revoked' / etc.
  target    text NOT NULL,           -- description of what was changed
  before    jsonb,
  after     jsonb,
  reason    text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_target ON permission_audit(target);
CREATE INDEX idx_audit_actor  ON permission_audit(actor_id);
```

## Effective-permissions query

```sql
-- Pull all permissions for a user, including via role hierarchy.
WITH RECURSIVE role_chain(id) AS (
    SELECT role_id FROM user_roles WHERE user_id = $userId
  UNION
    SELECT r.parent_role_id FROM roles r
      JOIN role_chain rc ON r.id = rc.id
     WHERE r.parent_role_id IS NOT NULL
)
SELECT rp.resource_pattern, p.action, p.resource_type, rp.decision
FROM role_chain rc
JOIN role_permissions rp ON rp.role_id = rc.id
JOIN permissions p       ON p.id       = rp.permission_id
UNION ALL
SELECT  resource_id::text AS resource_pattern,
        action,
        '_direct_' AS resource_type,
        decision
FROM resource_grants
WHERE user_id = $userId;
```

The recursive CTE walks the role hierarchy. Result is the set of `(action, resourcePattern, decision)` for the user.

Cache this in Redis under `userPerms:{userId}` with TTL 5 min.

## Resource ancestor query

```sql
WITH RECURSIVE ancestors(id, parent_id) AS (
    SELECT id, parent_id FROM resources WHERE id = $resourceId
  UNION ALL
    SELECT r.id, r.parent_id
    FROM resources r
    JOIN ancestors a ON r.id = a.parent_id
)
SELECT id FROM ancestors;
```

Result is the path from leaf to root. Cache under `ancestors:{resourceId}` with TTL 1 hr.

## Cycle detection on role hierarchy

When setting `roles.parent_role_id`, walk up; if you hit the role being modified, reject. Cheap; usually < 10 levels.

## Multi-tenant scoping

Every query filters on `tenant_id`. **Never query without it.** Use a row-level security policy or strict ORM contract.

## Indexes & partitioning

- `user_roles` partitioned by `tenant_id` for very large multi-tenant.
- `resources` partitioned by `tenant_id`.
- `permission_audit` partitioned by `created_at` (monthly).

## Output

```
Tables:    tenants / users / roles / permissions / role_permissions /
           user_roles / resource_grants / resources / permission_audit
Recursive: CTE to traverse role hierarchy + resource ancestor chain
Caching:   Redis: userPerms:{id}, ancestors:{id}; invalidate on writes
Tenancy:   tenant_id required on every read; row-level security on top
```
