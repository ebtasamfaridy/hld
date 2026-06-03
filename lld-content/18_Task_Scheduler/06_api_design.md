# 06 · Task Scheduler — API Design

## Library API (in-process)

```java
public interface Scheduler {
    JobHandle schedule(String name, Task task, Trigger trigger);
    JobHandle schedule(String name, Task task, Trigger trigger, RetryPolicy retry, Duration timeout);

    void cancel(JobHandle handle);
    void pause(JobHandle handle);
    void resume(JobHandle handle);

    Optional<Instant> nextFireTime(JobHandle handle);
    void start();
    void close();
}

public interface Task {
    void execute(TaskContext ctx) throws Exception;
}

public final class TaskContext {
    public String jobId();
    public Instant scheduledFor();
    public int attempt();
    public String idempotencyKey();
    public Map<String, Object> payload();
}
```

## REST API (distributed mode)

```
POST   /v1/jobs                                   { name, task, trigger, retry, ... }
GET    /v1/jobs                                   list (filter by state)
GET    /v1/jobs/{id}                              details
PATCH  /v1/jobs/{id}                              { paused: true } | { paused: false }
DELETE /v1/jobs/{id}                              cancel (soft)
POST   /v1/jobs/{id}/run-now                      one-time fire ignoring schedule

GET    /v1/jobs/{id}/executions?cursor=...        history of attempts

GET    /v1/dlq                                    dead-letter queue
POST   /v1/dlq/{id}/requeue                       admin requeue with reset attempts
```

### Trigger format in JSON

```json
{
  "kind": "cron",
  "spec": { "expression": "0 0 * * *", "timezone": "UTC" }
}
```

```json
{
  "kind": "fixed_rate",
  "spec": { "periodSec": 60, "startAt": "2026-04-29T00:00:00Z" }
}
```

```json
{
  "kind": "fixed_delay",
  "spec": { "delaySec": 30 }
}
```

```json
{
  "kind": "one_shot",
  "spec": { "fireAt": "2026-05-01T08:00:00Z" }
}
```

### Retry format

```json
{
  "maxAttempts": 5,
  "backoff": "EXPONENTIAL_JITTER",
  "baseMs": 5000
}
```

## Errors

| Code | Meaning | Caller |
| --- | --- | --- |
| 400 | Invalid trigger / cron | Fix |
| 404 | Job not found | Verify id |
| 409 | Concurrent edit (version mismatch) | Refresh + retry |
| 422 | Validation: timeout > visibility, etc. | Fix |
| 5xx | Backend | Retry |

## Worker → coordinator (internal)

```
GET    /worker/poll/{n}     → list of n claimed jobs (alternative to direct DB poll)
PUT    /worker/jobs/{id}/heartbeat        → extend lease
POST   /worker/jobs/{id}/complete         → mark success
POST   /worker/jobs/{id}/fail             → mark failed (with error)
```

In simpler designs, workers go directly to the DB. Adding a coordinator is for environments where workers can't reach the DB directly (security boundaries).

## Output

```
Library:    Scheduler / Task / TaskContext; familiar Quartz/cron semantics
REST:       CRUD jobs, list executions, DLQ requeue
Trigger:    JSON-discriminated by 'kind' (cron/fixed_rate/fixed_delay/one_shot)
Errors:     409 for concurrent edits; 422 for invalid configs
```
