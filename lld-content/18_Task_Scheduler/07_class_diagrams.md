# 07 · Task Scheduler — Class Diagrams

## In-process scheduler

```mermaid
classDiagram
    class Scheduler {
      <<interface>>
      +schedule(name, task, trigger) JobHandle
      +cancel(handle)
      +pause(handle) / resume(handle)
      +start() / close()
    }
    class InProcessScheduler {
      -store: JobStore
      -delayQueue: DelayQueue
      -ticker: Thread
      -executor: ExecutorService
    }
    Scheduler <|.. InProcessScheduler

    class Trigger {
      <<interface>>
      +nextFireTime(prev) Instant?
    }
    class OneShotTrigger
    class FixedRateTrigger
    class FixedDelayTrigger
    class CronTrigger
    Trigger <|.. OneShotTrigger
    Trigger <|.. FixedRateTrigger
    Trigger <|.. FixedDelayTrigger
    Trigger <|.. CronTrigger

    class Task { <<interface>> +execute(ctx) }

    class RetryPolicy {
      -maxAttempts: int
      -backoff: BackoffStrategy
      +shouldRetry(attempt) boolean
      +nextDelay(attempt) Duration
    }
    class BackoffStrategy { <<interface>> +delay(attempt) Duration }
    class FixedBackoff
    class LinearBackoff
    class ExponentialBackoffWithJitter
    BackoffStrategy <|.. FixedBackoff
    BackoffStrategy <|.. LinearBackoff
    BackoffStrategy <|.. ExponentialBackoffWithJitter

    class JobStore {
      <<interface>>
      +put(job)
      +get(id) Job
      +remove(id)
      +listDue(now, limit) List~Job~
      +markCompleted/Failed(id, ...)
    }
    class InMemoryJobStore
    class PostgresJobStore
    JobStore <|.. InMemoryJobStore
    JobStore <|.. PostgresJobStore

    InProcessScheduler o-- JobStore
    InProcessScheduler ..> Trigger
    InProcessScheduler ..> Task
```

## Distributed worker

```mermaid
classDiagram
    class Worker {
      -id: string
      -store: JobStore
      -registry: TaskRegistry
      -concurrency: int
      -leaseExtender: ScheduledExecutor
      +start() / stop()
      -claimAndRun()
    }

    class TaskRegistry {
      +register(name, task)
      +lookup(name) Task
    }

    class CoordinatorService {
      <<optional>>
      +reclaimExpiredLeases()
      +handleMisfires()
    }

    Worker o-- JobStore
    Worker o-- TaskRegistry
    CoordinatorService ..> JobStore
```

## Package layout (`com.scheduler`)

```
api/         Scheduler, Task, JobHandle, TaskContext
core/        Job, JobState, RetryPolicy, BackoffStrategy + Fixed/Linear/Exp,
             Execution, MisfirePolicy
trigger/     Trigger + OneShotTrigger/FixedRateTrigger/FixedDelayTrigger/CronTrigger
store/       JobStore + InMemoryJobStore (+ PostgresJobStore stub)
executor/    InProcessScheduler
worker/      Worker + TaskRegistry (+ CoordinatorService stub)
```

## Why these abstractions

### `Trigger` as Strategy
4 trigger types now; more later (calendar, dependency-on-other-job). The scheduler doesn't care; it asks `trigger.nextFireTime(prev)`.

### `BackoffStrategy` as Strategy
Same story; pluggable.

### `JobStore` as a portability boundary
In-memory vs Postgres vs Redis. The scheduler logic stays the same.

### `Worker` separated from `Scheduler`
Two roles:
- **Scheduler** decides *when* a job runs.
- **Worker** does the actual *executing*.

In single-process, they're combined. In distributed, they split: many workers, one (or a leader-elected) scheduler.

### `TaskRegistry` for distributed mode
A job's task is stored as `task_class` (string) in the DB. The worker looks it up in the local `TaskRegistry` to instantiate. This decouples definition from binary.

## Output

```
Strategy:    Trigger (nextFireTime); BackoffStrategy (delay); JobStore (storage)
Components:  Scheduler (planner) + Worker (executor) + TaskRegistry
Layered:     api → core → trigger / store / executor / worker
Portable:    InMemoryJobStore for tests; PostgresJobStore for production
```
