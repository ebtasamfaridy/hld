# 02 · Task Scheduler — Capacity Estimation

## Scale (mid-size enterprise)

```
Tenants:                 1 K
Tasks per tenant:        100 (avg)
Total tasks:             100 K
Trigger fires / sec peak: 1 K (peak); 100 sustained
Avg execution duration:  500 ms
Workers:                 50
Worker concurrency each: 16 threads
Cluster concurrent execs: 800
```

## Throughput math

```
1 K fires/sec × 500 ms duration = 500 concurrent execs at peak
Cluster has 800 slots → 60 % utilization at peak. Safe margin.
```

If executions are slow (5 s each), at 1 K fires/sec we need 5000 concurrent slots → many more workers, or queue and let some lag.

## Storage

```
jobs table:        100 K rows × 1 KB = 100 MB
executions table:  100 / sec × 86 400 sec × 90 days × 1 KB = 750 GB / 3 months
                   → partition by date; retain 30 days hot, ship cold to S3
audit:             ~100 GB / year
```

## Hot ops

| Op | Cost | Where |
| --- | --- | --- |
| `claim 10 due jobs` | ~5 ms | DB `SELECT FOR UPDATE SKIP LOCKED` + UPDATE |
| `execute job` | task-dependent | worker thread |
| `mark complete` | ~5 ms | DB UPDATE |
| `mark failed + reschedule` | ~10 ms | DB UPDATE |
| `compute next fire time (cron)` | <100 µs | parse + compute |

## Bottlenecks

| Hot spot | Mitigation |
| --- | --- |
| Many workers polling DB | `LISTEN/NOTIFY` for push; or backoff polling |
| Same job claimed twice | Atomic `UPDATE jobs SET claimed_by=$worker WHERE id=$id AND claimed_by IS NULL` |
| Workers too slow | Add workers; horizontal scale |
| DB write contention on executions table | Partition by date; bulk inserts |
| Misfire storm after outage | Misfire policy = `RUN_NOW`, not `RUN_ALL` |

## Why durable storage

In-process schedulers lose state on crash. Distributed schedulers must persist:
- Triggers (so we know when to fire).
- Last fired time (so we don't double-fire).
- In-flight executions (so a crashed worker doesn't lose them).
- Retry counters.

Postgres is the workhorse here. Redis works for purely in-memory low-stakes use cases.

## What forces design

1. **`SELECT FOR UPDATE SKIP LOCKED`** — atomic claim of due jobs without contention.
2. **Visibility timeout** — handles worker crashes.
3. **Retry with backoff** — handles transient failures.
4. **Idempotency** — required because of at-least-once semantics.
5. **Misfire policy** — handles downtime gracefully.

## Output

```
Scale:         100K tasks; 1K peak fires/sec; 800 concurrent execs across 50 workers
Storage:       100MB jobs; ~250GB/month executions log
Bottlenecks:   poll storm (LISTEN/NOTIFY), claim race (atomic UPDATE), misfire flood
Required:      atomic claim, visibility timeout, retry backoff, idempotency, misfire policy
```
