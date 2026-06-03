# 13 · Task Scheduler — Extensions & Tradeoffs

## Extensions

### 1. Distributed mode (Postgres-backed)
Move the `JobStore` to Postgres. Workers claim with `FOR UPDATE SKIP LOCKED`. Lease + heartbeat for crash safety.

### 2. Cron expression — full grammar
Support all 5 fields with `*`, `,`, `-`, `/`, day-of-week. Use `cron-utils` library or write a parser.

### 3. Timezones
Each job has a timezone. Cron expressions evaluated in that TZ. Store in UTC; convert when computing fire time.

### 4. Calendar-based exclusions
"Run nightly except on US holidays." A `Calendar` interface that says `isExcluded(date)`. Trigger.nextFireTime skips excluded dates.

### 5. Job dependencies (DAG)
Job B runs only after Job A succeeds. Build a graph of triggers; on a node's success, fire dependent nodes. Airflow's territory.

### 6. Priority
Multiple jobs due at once — pick higher-priority first. Add `priority` column; ORDER BY priority DESC, next_fire_at ASC in claim query.

### 7. Tenancy isolation
Per-tenant rate limit on concurrent executions. Queue rest. Fair scheduling across tenants.

### 8. Conditional triggers
"Only run if the previous instance succeeded" or "only run if a flag is on." Pre-condition check at fire time; skip if false.

### 9. SLA / alerting
Track expected duration; alert if a job runs > 2× expected. Alert if a job's next fire is in the past by > N minutes (suggests stuck workers).

### 10. Catch-up replay
Re-run a job for a backfill window. "Run yesterday's daily-report" — manual trigger with a fake `scheduledFor`.

## Tradeoffs

### In-process vs distributed

| In-process | Distributed |
| --- | --- |
| Trivial to deploy | Requires shared store + workers |
| Lost on JVM restart | Durable across restarts |
| Single host limits | Horizontal scale |
| **Pick**: in-process for app-internal scheduling; distributed for batch / data pipelines |

### Postgres vs Redis vs Kafka

| Postgres | Redis | Kafka |
| --- | --- | --- |
| ACID; FOR UPDATE SKIP LOCKED | Sorted sets + Lua | Append-only |
| Easy CRUD on jobs | Mostly fire-and-forget | Streams |
| Pause/resume easy | Awkward | Hard |
| **Pick**: Postgres is the safe bet; Redis when ultra-light |

### Misfire policy

| IGNORE | RUN_NOW | RUN_ALL |
| --- | --- | --- |
| Skip missed fires | Run once to catch up | Run every missed fire |
| Safest | Default | Risky on long downtime |

### At-least-once vs exactly-once

True exactly-once requires the task itself to be transactional with the job-state update. Hard. Most systems are at-least-once + idempotent tasks; this is the practical answer.

### Polling vs LISTEN/NOTIFY

| Polling | LISTEN/NOTIFY |
| --- | --- |
| Simple | Lower idle DB load |
| Constant DB cost | Driver complexity |
| **Pick**: polling first; add NOTIFY when DB load shows up |

### Trigger persistence vs computed nextFireTime

We compute `nextFireTime` on demand from the trigger. Storing all future fires would explode for cron-style triggers. Only the next is materialized.

## Open questions

- How do we handle a job that takes longer than its period in fixed-rate mode? (Skip overlap or queue.)
- Should pause persist across restarts? (Yes; pause is part of job state.)
- Manual run-now: should it reset retry counters? (Yes for "give it another go".)
- Should we preserve all execution history or roll up? (Hot 30 days; cold S3.)

## Output

```
Extensions:    distributed mode, full cron grammar, TZ, calendars, DAG,
               priority, tenancy isolation, conditional triggers, SLA alerts, replay
Tradeoffs:     in-process vs distributed; Postgres vs Redis vs Kafka;
               misfire policy; at-least-once vs exactly-once; poll vs notify
Pre-decided:   FOR UPDATE SKIP LOCKED for claim; lease + heartbeat for crash safety;
               idempotency keys per attempt; compute nextFireTime on demand
Open Qs:       fixed-rate overlap, pause persistence, run-now reset, history retention
```
