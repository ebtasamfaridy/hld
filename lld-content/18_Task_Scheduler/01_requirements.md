# 01 · Task Scheduler — Requirements

## Functional requirements

### Core
- `schedule(task, trigger)` — register a task to run per a trigger.
- Trigger types:
  - **One-shot at T**: run once at a specific instant.
  - **Fixed rate**: every N seconds (target firing time = startTime + k×N).
  - **Fixed delay**: wait N seconds *after* the previous run finishes.
  - **Cron**: per a cron expression.
- **Cancel** a scheduled task.
- **Pause / resume** a scheduled task.
- **List** scheduled tasks; inspect next fire time.
- **Retry policy** per task: maxAttempts + backoff.
- **Timeout** per execution; if exceeded, terminate.
- **Idempotency key** per execution attempt for at-least-once safety.

### Distributed mode
- Multiple worker processes share a job store.
- Workers **claim** due jobs atomically (no two workers run the same execution).
- **Visibility timeout**: if a worker crashes mid-execution, the job becomes claimable again after a TTL.
- **Failed jobs** retried per policy; eventual move to **dead-letter queue**.
- **Misfire policy**: `IGNORE` (skip), `RUN_NOW` (catch up once), `RUN_ALL` (run all missed).

### Out of scope (V2)
- DAG scheduling (Airflow-style task graphs with dependencies).
- Conditional triggers (only run if X holds).
- Calendar-based exclusions (don't run on holidays).
- Timezone-aware crons (V1 in UTC; V2 per-task TZ).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| In-process scheduling latency | < 10 ms drift from due time | accuracy |
| Distributed claim latency | < 100 ms | multiple workers competing |
| Throughput | 10 K tasks/sec across cluster | typical batch workload |
| Durability | 100 % (jobs never lost) | distributed mode |
| At-least-once delivery | guaranteed | idempotent tasks tolerate this |
| At-most-once option | supported | for non-idempotent jobs (rare) |
| Availability | 99.99 % | infra component |

## Actors

```
Application       - registers jobs and triggers
Scheduler         - in-process planner (which job is next)
Worker            - executes claimed jobs (pool of threads)
Coordinator       - in distributed mode; tracks claims, leases, misfires
Job store         - DB (Postgres) for distributed mode; in-memory for single
Dead letter       - queue of jobs that exceeded retry policy
Admin             - lists / pauses / cancels jobs
```

## Edge cases

| Case | Handling |
| --- | --- |
| System clock jumps forward | All due jobs fire on next tick; misfire policy applies |
| System clock jumps backward | Don't re-run already-fired jobs; track last-fired |
| Worker crashes mid-execution | Visibility timeout → another worker claims |
| Two workers claim same job | Atomic claim (PK on `claimed_by` or row-level lock) prevents this |
| Job taking longer than visibility timeout | Worker periodically extends lease |
| Cron expression invalid | Reject at registration time |
| Job throws an exception | Per retry policy; exhaust attempts → DLQ |
| Many jobs due at the same instant | Bound concurrent executions; queue rest |
| Database transient failure | Retry with backoff; block claims |
| Daylight saving / timezone | Fix in UTC for V1; document |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| In-process scheduler with DelayQueue | ✓ | |
| Triggers: one-shot, fixed rate, fixed delay, cron | ✓ | |
| Retry with exponential backoff + jitter | ✓ | |
| Distributed mode w/ Postgres `FOR UPDATE SKIP LOCKED` | ✓ | |
| Misfire policy | ✓ | |
| Dead-letter queue | ✓ | |
| Pause / resume / cancel | ✓ | |
| Per-task timezone | | ✓ |
| Holiday calendars | | ✓ |
| DAG / dependency graphs (Airflow-style) | | ✓ |
| Distributed leader election (controller) | basic | full Raft / etcd |

## Output

```
Core:    schedule + cancel; one-shot / fixed-rate / fixed-delay / cron triggers;
         retry policy; idempotency key; pause/resume; misfire policy
NFR:     <10ms drift in-process; <100ms claim distributed; 10K/sec; durable;
         at-least-once with idempotency
Edge:    clock jumps, worker crashes, lease extension, claim race, DLQ
```
