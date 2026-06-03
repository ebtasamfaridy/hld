# 10 · Task Scheduler — Design Patterns

## 1. Strategy — `Trigger`
4 trigger kinds, possibly more. Same interface; pluggable.

## 2. Strategy — `BackoffStrategy`
Fixed / Linear / Exponential / ExponentialWithJitter.

## 3. Strategy — `JobStore`
In-memory / Postgres / Redis. Same interface.

## 4. Repository — `JobStore` + `ExecutionStore`
DB abstraction.

## 5. Command — `Task`
Each task is a command object encapsulating its execution. Workers iterate and `execute()`.

## 6. Producer-Consumer — Scheduler ↔ DelayQueue ↔ Workers
Classic. Scheduler enqueues; DelayQueue holds by deadline; workers consume.

## 7. State pattern — Job state machine
Light: enum + transition guards in service. Full state pattern for V2 if states gain behavior.

## 8. Optimistic concurrency — `version` column on `jobs`
Two writers don't clobber. Workers' `UPDATE … WHERE claimed_by = $me` is the same idea: ownership-checked update.

## 9. Visibility timeout (queue pattern)
`lease_until` + `claimed_by`. Borrowed from message queues (SQS, RabbitMQ).

## 10. Outbox / DLQ
Permanently-failed jobs land in DLQ for manual review.

## 11. Circuit breaker (V2)
If a task is failing too often, skip its retries for a cooldown period. Prevents flooding a downstream that's already in trouble.

## 12. Idempotency token
`(jobId, scheduledFireTime)` is unique per logical execution. Tasks use it to dedup their own work.

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| Single global lock during claim | Use `FOR UPDATE SKIP LOCKED` for parallelism |
| Scheduling future fires by storing every fire-time | Compute `nextFireTime` on demand |
| Fan-out to all workers on every fire | Atomic claim ensures one worker per fire |
| `SELECT … FOR UPDATE` without `SKIP LOCKED` | Workers serialize — kills throughput |
| Re-running tasks blindly | Idempotency keys; dedup on the task side |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Trigger / Backoff / JobStore | Pluggable trigger kinds, retry policies, storage |
| Repository | JobStore / ExecutionStore | DB abstraction |
| Command | Task | Encapsulated unit of work |
| Producer-Consumer | DelayQueue + Workers | Scheduling + execution decoupling |
| State pattern | Job FSM | SCHEDULED → CLAIMED → RUNNING → SUCCEEDED \| FAILED \| DLQ |
| Optimistic concurrency | version + claimed_by | Concurrent admin & worker safety |
| Visibility timeout | lease_until | Worker-crash recovery |
| DLQ | dead_letter | Permanently failed jobs |
| Idempotency | (jobId, scheduledFireTime) | Safe at-least-once execution |

## Output

```
The system is Strategy (trigger, backoff, store) + Repository + Producer-Consumer
+ State machine + Visibility-timeout / DLQ from queue patterns.
The hot path is "claim due jobs atomically; execute; mark complete or retry".
```
