# 11 · Task Scheduler — Concurrency & Scaling

## In-process

### `DelayQueue` is the right primitive
Java's `DelayQueue<E extends Delayed>` is a min-heap ordered by `getDelay()`. `take()` blocks until the head is due. Producers `put`; the ticker thread `take`s.

This is exactly what an in-process scheduler needs.

### Thread pool sizing
- CPU-bound tasks: `nCpu` threads.
- I/O-bound tasks: `nCpu × (1 + waitTime/computeTime)` threads. Often 50–200 for I/O-heavy schedulers.

### Avoid blocking the ticker
The ticker thread takes from the queue and **submits** to the pool — never executes inline. Otherwise a slow task delays every other due job.

## Distributed

### `SELECT FOR UPDATE SKIP LOCKED` is the magic
Many workers polling the same query. Postgres serializes inside, but with `SKIP LOCKED` each worker grabs different rows. No two workers see the same job. No deadlock. No retry loop.

```sql
SELECT id FROM jobs WHERE state='SCHEDULED' AND next_fire_at <= now()
ORDER BY next_fire_at LIMIT 10 FOR UPDATE SKIP LOCKED;
```

### Reducing poll noise
50 workers polling every 1s = 50 RPS on the DB even when there's nothing to do. Mitigations:
- **`LISTEN/NOTIFY`** (Postgres): scheduler `NOTIFY`s when new jobs are added; workers consume notifications and poll only then.
- **Adaptive backoff**: when polls return empty, increase poll interval (1s → 5s → 30s); reset to 1s when work appears.
- **Batched claim**: claim N jobs at once, not one.

### Lease extension via heartbeat
Long tasks need to extend their lease. Background `ScheduledExecutor` per worker that updates `lease_until` every 20 s for tasks taking > 10 s. Skip for short tasks.

### Lease expiry race
Worker A's lease expires at t=30; Worker B claims at t=31. Worker A finishes at t=32 and tries to mark complete.

The mark-complete query uses `WHERE claimed_by = $me`. Worker A's `claimed_by` no longer matches — the row was reclaimed by Worker B. Worker A's update affects 0 rows. Detect this and discard the result. Worker B will run it again. **Idempotency saves us here.**

### Many jobs due at the same instant
A nightly cron at midnight: 1000 jobs all due at 00:00. Workers grab batches; the queue drains over a few seconds.

If this is unacceptable (need them all done in the first second), increase worker count or split jobs across staggered times (`00:00:01`, `00:00:02`, ...).

### Clock skew
Source of truth is the DB server time. Workers don't compare to their own clock for `next_fire_at` — they trust the DB's `now()`. This avoids drift across many machines.

The exception: in-process schedulers with no DB use `System.currentTimeMillis()`. NTP sync mitigates skew.

## Scaling

| Knob | Scale axis |
| --- | --- |
| Add workers | Concurrent execution |
| Larger claim batch | Fewer DB round trips per fire |
| `LISTEN/NOTIFY` | Lower idle DB load |
| Sharded job table | Higher write throughput |
| Hot partition (`tenant_id`) | Per-tenant isolation |

## Hot bottlenecks

| Bottleneck | Mitigation |
| --- | --- |
| Worker poll storm | LISTEN/NOTIFY + adaptive backoff |
| DB write IOPS for executions | Bulk insert; partition by date |
| Single jobs table contention | Composite index; partition by next_fire_at |
| Slow tasks blocking lease extension | Dedicated heartbeat thread |
| One bad job retrying forever | Bounded retries + DLQ |

## Failure modes

| Failure | Behavior |
| --- | --- |
| Worker crash | Lease expires; another worker re-claims; idempotent task |
| DB connection drop mid-claim | Tx aborts; rows unlocked; next poll retries |
| Task hangs forever | Timeout enforces termination (Future.cancel(true) or thread interruption) |
| Long downtime → many missed fires | Misfire policy: `RUN_NOW` typical |
| All workers down | Jobs accumulate; resume cleanly when any worker comes up |
| Database down | Workers fail to claim; back off; resume when DB returns |

## Output

```
In-process:  DelayQueue + thread pool; ticker submits, never executes
Distributed: FOR UPDATE SKIP LOCKED claim; LISTEN/NOTIFY for low idle load;
             lease + heartbeat for crash recovery; idempotency for safety
Scale:       add workers; bigger batches; partition tables; tenant isolation
Failure:     visibility timeout fixes worker crashes; DLQ for poison jobs
```
