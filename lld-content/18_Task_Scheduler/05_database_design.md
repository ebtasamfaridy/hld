# 05 · Task Scheduler — Database Design

## Postgres schema

```sql
-- A scheduled job (the "definition" + current schedule state).
CREATE TABLE jobs (
  id              uuid PRIMARY KEY,
  name            text NOT NULL,
  task_class      text NOT NULL,
  payload         jsonb NOT NULL DEFAULT '{}',
  trigger_kind    text NOT NULL,            -- one_shot|fixed_rate|fixed_delay|cron
  trigger_spec    jsonb NOT NULL,           -- {expr:'0 0 * * *'} or {periodSec:60} etc.
  retry_max       int  NOT NULL DEFAULT 3,
  retry_backoff   text NOT NULL DEFAULT 'EXPONENTIAL_JITTER',
  retry_base_ms   int  NOT NULL DEFAULT 5000,
  timeout_ms      int  NOT NULL DEFAULT 60000,
  misfire_policy  text NOT NULL DEFAULT 'RUN_NOW',  -- IGNORE|RUN_NOW|RUN_ALL
  state           text NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED|CLAIMED|RUNNING|PAUSED|FAILED|SUCCEEDED|DLQ
  next_fire_at    timestamptz NOT NULL,
  last_fire_at    timestamptz,
  attempt         int  NOT NULL DEFAULT 0,
  claimed_by      text,
  lease_until     timestamptz,
  version         bigint NOT NULL DEFAULT 1,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Critical index: workers query by state + next_fire_at constantly.
CREATE INDEX idx_jobs_due
  ON jobs (next_fire_at)
  WHERE state IN ('SCHEDULED','FAILED');

CREATE INDEX idx_jobs_lease
  ON jobs (lease_until)
  WHERE state='CLAIMED';

-- An execution attempt — append-only.
CREATE TABLE executions (
  id              uuid PRIMARY KEY,
  job_id          uuid NOT NULL,
  attempt         int  NOT NULL,
  scheduled_for   timestamptz NOT NULL,    -- when it was supposed to fire
  started_at      timestamptz NOT NULL DEFAULT now(),
  finished_at     timestamptz,
  worker_id       text NOT NULL,
  outcome         text,                    -- SUCCESS|FAILURE|TIMEOUT|CANCELED
  error           text,
  idempotency_key text NOT NULL,
  UNIQUE (job_id, attempt, scheduled_for)
);

CREATE INDEX idx_exec_job ON executions(job_id, started_at DESC);

-- Permanent failures (no further retries).
CREATE TABLE dead_letter (
  job_id          uuid PRIMARY KEY,
  payload         jsonb NOT NULL,
  failed_attempts int  NOT NULL,
  first_failed_at timestamptz NOT NULL,
  last_error      text,
  moved_at        timestamptz NOT NULL DEFAULT now()
);
```

## Atomic claim query

```sql
WITH due AS (
  SELECT id
  FROM jobs
  WHERE state = 'SCHEDULED' AND next_fire_at <= now()
  ORDER BY next_fire_at
  LIMIT 10
  FOR UPDATE SKIP LOCKED
)
UPDATE jobs
SET state='CLAIMED',
    claimed_by=$worker,
    lease_until=now() + interval '30 seconds',
    version=version+1,
    updated_at=now()
WHERE id IN (SELECT id FROM due)
RETURNING id, name, task_class, payload, attempt, retry_max, retry_backoff, retry_base_ms, timeout_ms;
```

`FOR UPDATE SKIP LOCKED` is **the** primitive: many workers can poll concurrently, each sees a different subset.

## Lease reclaim query (separate background process)

```sql
UPDATE jobs
SET state='SCHEDULED',
    claimed_by=NULL,
    lease_until=NULL,
    version=version+1
WHERE state='CLAIMED' AND lease_until < now();
```

Or simpler: include expired-lease rows directly in the claim query.

## Mark complete

```sql
UPDATE jobs
SET state=CASE
        WHEN $isOneShot THEN 'SUCCEEDED'
        ELSE 'SCHEDULED' END,
    last_fire_at=$scheduledFor,
    next_fire_at=$nextFireFromTrigger,
    attempt=0,
    claimed_by=NULL,
    lease_until=NULL,
    version=version+1
WHERE id=$id AND claimed_by=$worker;
```

The `claimed_by=$worker` ownership check ensures we don't accidentally update a job that some other worker re-claimed after our lease expired.

## Mark failed + retry

```sql
UPDATE jobs
SET state='FAILED',
    next_fire_at=now() + ($backoffMs || ' milliseconds')::interval,
    attempt=attempt+1,
    claimed_by=NULL,
    lease_until=NULL,
    version=version+1
WHERE id=$id AND claimed_by=$worker;
```

If `attempt+1 > retry_max`, transition to DLQ instead.

## Why Postgres (vs Redis / Kafka)?

| Postgres | Redis | Kafka |
| --- | --- | --- |
| Strong durability + ACID | In-memory; AOF for durability | Append-only durable |
| `FOR UPDATE SKIP LOCKED` | sorted sets + Lua scripts | partition assignment |
| Indexed query on `next_fire_at` | sorted by score | not designed for delayed work |
| Easy pause/cancel via UPDATE | possible but messier | hard |
| **Pick**: Postgres for general scheduler; Redis for low-stakes queues; Kafka for high-throughput non-scheduled streams |

## Output

```
jobs:        definition + current schedule state; FOR UPDATE SKIP LOCKED claims
executions:  append-only attempts; UNIQUE (job, attempt, scheduled_for) idempotency
dead_letter: failed-too-many; preserved for human inspection
Indexes:     on next_fire_at (partial: due rows only); on lease_until (claimed rows)
```
