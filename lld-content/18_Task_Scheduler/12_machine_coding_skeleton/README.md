# 12 · Task Scheduler — Machine Coding Skeleton

In-process scheduler with `DelayQueue`, pluggable triggers, retry policy, and idempotency keys.

```
src/main/java/com/scheduler/
├── api/         Scheduler, Task, TaskContext, JobHandle
├── core/        Job, JobState, RetryPolicy, BackoffStrategy +
│                FixedBackoff/ExponentialBackoffWithJitter, Execution
├── trigger/     Trigger, OneShotTrigger, FixedRateTrigger, FixedDelayTrigger,
│                CronTrigger (simple)
├── store/       JobStore (interface), InMemoryJobStore
├── executor/    InProcessScheduler
├── worker/      (placeholder for distributed worker)
└── Main.java
```

## Demo
1. Schedule a fixed-rate task firing every 200 ms.
2. Schedule a one-shot task in 500 ms.
3. Schedule a flaky task that fails twice then succeeds; show retry+backoff.
4. Schedule a cron task `*/1 * * * * *` (every second).
5. Run for 3 seconds; print outcomes; verify idempotency keys are unique per attempt.
