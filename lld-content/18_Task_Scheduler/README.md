# 18 · Task Scheduler (Quartz / Airflow / cron-like)

> Job scheduler interviews mix three things: in-process scheduling primitives (priority queue), distributed execution (workers + leader election), and the algorithms behind cron + retries + idempotency.

## What you will master

- **Two scheduler classes**: in-process (Java `ScheduledExecutorService`-style) and distributed (multi-worker, leader-elected, durable).
- **Triggers**: one-shot, fixed-rate, fixed-delay, cron, calendar.
- **Cron expression parsing** and next-fire-time computation.
- **Min-heap / DelayQueue** for in-process priority by next fire time.
- **Distributed coordination**: claiming jobs via `SELECT FOR UPDATE SKIP LOCKED` or Redis locks.
- **Misfires & catch-up**: jobs that should have run during downtime.
- **Retries with backoff**: linear, exponential, jitter.
- **Idempotency** (the same logical job must not run twice).
- **Visibility timeouts** for crashed workers.
- **Backpressure** when the worker pool is saturated.
- **Calendar exclusions** (don't run on holidays).
- **Distributed dead-letter** queue for permanently failing jobs.

## Read order

| # | File |
| - | --- |
| 1 | [01_requirements.md](./01_requirements.md) |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) |
| 3 | [03_hld.md](./03_hld.md) |
| 4 | [04_domain_model.md](./04_domain_model.md) |
| 5 | [05_database_design.md](./05_database_design.md) |
| 6 | [06_api_design.md](./06_api_design.md) |
| 7 | [07_class_diagrams.md](./07_class_diagrams.md) |
| 8 | [08_sequence_diagrams.md](./08_sequence_diagrams.md) |
| 9 | [09_state_machines.md](./09_state_machines.md) |
| 10 | [10_design_patterns.md](./10_design_patterns.md) |
| 11 | [11_concurrency_and_scaling.md](./11_concurrency_and_scaling.md) |
| 12 | [12_machine_coding_skeleton/](./12_machine_coding_skeleton/) |
| 13 | [13_extensions_and_tradeoffs.md](./13_extensions_and_tradeoffs.md) |
| 14 | [14_interviewer_followups.md](./14_interviewer_followups.md) |

## Headline tradeoffs

- **In-process** = `DelayQueue` + thread pool. Solves 80 % of interview questions.
- **Distributed** = DB-backed jobs + workers polling with `FOR UPDATE SKIP LOCKED`.
- **Cron** is a Strategy (`Trigger`); the engine doesn't care about expression details.
- **Idempotency** is **mandatory** — at-least-once delivery is the realistic guarantee.
- **Misfires** must have a policy (drop / catch-up-once / catch-up-all).
