# 05 · Feature Flag System — Database Design

## Postgres schema

```sql
CREATE TABLE environments (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,                    -- dev / staging / prod
  workspace   text NOT NULL,
  UNIQUE (workspace, name)
);

CREATE TABLE flags (
  id              uuid PRIMARY KEY,
  environment_id  uuid NOT NULL REFERENCES environments(id),
  flag_key        text NOT NULL,
  enabled         boolean NOT NULL DEFAULT true,    -- kill switch (true = use rules; false = always offVariation)
  variations      jsonb NOT NULL,                   -- [{id, value}, ...]
  fallthrough_variation_id text NOT NULL,
  off_variation_id text NOT NULL,
  targeting_rules jsonb NOT NULL DEFAULT '[]',      -- ordered list
  prerequisites   jsonb NOT NULL DEFAULT '[]',
  version         bigint NOT NULL DEFAULT 1,
  metadata        jsonb NOT NULL DEFAULT '{}',
  archived        boolean NOT NULL DEFAULT false,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (environment_id, flag_key)
);

CREATE INDEX idx_flags_env ON flags(environment_id) WHERE archived = false;

CREATE TABLE flag_audit (
  id          uuid PRIMARY KEY,
  flag_id     uuid NOT NULL,
  environment_id uuid NOT NULL,
  flag_key    text NOT NULL,
  actor_id    uuid NOT NULL,
  action      text NOT NULL,             -- created / updated / archived / restored
  before      jsonb,
  after       jsonb,
  reason      text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_flag ON flag_audit(flag_id, created_at DESC);
CREATE INDEX idx_audit_env  ON flag_audit(environment_id, created_at DESC);
```

### Why JSONB for `targeting_rules`?
Targeting rules are *deeply nested but rarely queried by indexed fields*. Storing as JSONB keeps the schema simple. We don't need to index inside rules; the SDK pulls the whole flag.

If we ever need server-side queries like "which flags target country=IN", we add a generated column or an inverted index.

### Why a separate `flag_audit` table?
- Append-only.
- Independent retention (long).
- Different access pattern (rare reads, sequential writes).
- Different security policy (admins can read all audit; can't delete).

## Update bus topic

```
topic: flag.updates
key:   environmentId + ":" + flagKey
value: {
   environmentId, flagKey, version,
   action: 'updated' | 'created' | 'archived',
   payload: <full flag JSON>
}
```

Keying by `env+key` ensures order per flag — a server that has applied v17 won't apply v15 later.

## Redis cache (read-side optimization)

```
flag:{env}:{key}        →  full flag JSON  (TTL 60s)
flag:{env}:list         →  zset of flag keys
flag:{env}:version      →  monotonic version (incr on any change)
```

Used by:
- Admin API for fast reads.
- CDN snapshot generator.
- SDK polling fallback (when SSE fails).

## CDN snapshot

Periodically (every 30s), publish a JSON file per environment:

```
GET https://cdn.example.com/sdk/v1/snapshots/{envId}/v{N}.json
```

The version is monotonic. SDKs can `If-None-Match` against the last seen ETag.

This makes bootstrap a CDN-cached file fetch — close to 0 origin load even with 500 K SDKs starting up after a deploy.

## Partitioning

`flag_audit` partitioned by `created_at` monthly. Old partitions rolled to S3 cold storage.

`flags` table is small (20 K rows) — no partitioning needed.

## Output

```
Truth:           Postgres (flags, environments, flag_audit)
Cache:           Redis (per-flag JSON, list, version)
Distribution:    Kafka topic 'flag.updates' keyed by env+key
SDK bootstrap:   CDN snapshot per environment, versioned
Audit:           append-only, partitioned, retained long
```
