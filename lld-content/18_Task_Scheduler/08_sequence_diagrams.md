# 08 · Task Scheduler — Sequence Diagrams

## 1. In-process: schedule → fire → execute → reschedule

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Sch as Scheduler
    participant DQ as DelayQueue
    participant Pool as Worker pool
    participant Task

    App->>Sch: schedule("daily-report", task, cron)
    Sch->>Sch: trigger.nextFireTime(null) → t0
    Sch->>DQ: enqueue(jobId, fireAt=t0)
    Note over DQ: ticker thread blocks on take()

    DQ-->>Sch: jobId due
    Sch->>Pool: submit(execute(jobId))
    Pool->>Task: execute(ctx)
    Task-->>Pool: success
    Pool->>Sch: completed(jobId, t0)
    Sch->>Sch: trigger.nextFireTime(t0) → t1
    Sch->>DQ: enqueue(jobId, fireAt=t1)
```

## 2. In-process: failure + retry

```mermaid
sequenceDiagram
    autonumber
    participant Sch as Scheduler
    participant Pool
    participant Task
    participant DQ

    Sch->>Pool: submit(execute(jobId, attempt=1))
    Pool->>Task: execute
    Task--xPool: throws
    Pool->>Sch: failed(jobId, attempt=1, err)
    Sch->>Sch: retry.shouldRetry(1) → true
    Sch->>Sch: delay = retry.nextDelay(1) = 5s + jitter
    Sch->>DQ: enqueue(jobId, fireAt=now+5s, attempt=2)
```

## 3. Distributed: claim + execute + complete

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker
    participant DB as Postgres
    participant Task

    W->>DB: BEGIN
    W->>DB: SELECT id ... FOR UPDATE SKIP LOCKED LIMIT 10
    DB-->>W: [j1, j2, j3]
    W->>DB: UPDATE state='CLAIMED', claimed_by=W, lease_until=now+30s
    W->>DB: COMMIT

    par for each job
      W->>Task: execute(ctx with idempotencyKey)
      Task-->>W: success
      W->>DB: UPDATE state=SCHEDULED, last_fire=t0,<br/>next_fire=trigger.next(t0), attempt=0,<br/>claimed_by=NULL WHERE claimed_by=W
    end
```

## 4. Distributed: worker crash → re-claim

```mermaid
sequenceDiagram
    autonumber
    participant W1 as Worker A
    participant DB
    participant W2 as Worker B

    W1->>DB: claim j1, lease=t+30s
    W1->>W1: starts task...
    Note over W1: process killed at t+5s

    Note over DB: lease still says t+30s — nothing happens for 25s

    W2->>DB: SELECT WHERE state='CLAIMED' AND lease_until<now() FOR UPDATE SKIP LOCKED
    DB-->>W2: j1
    W2->>DB: UPDATE state='CLAIMED', claimed_by=W2, lease_until=new
    Note over W2: re-execute (at-least-once) — task is idempotent
```

## 5. Distributed: lease extension during long task

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker
    participant DB
    participant Task

    W->>DB: claim, lease=t+30s
    W->>Task: execute (will take 2 min)

    loop every 20s while running
      W->>DB: UPDATE lease_until=now+30s WHERE id=$ AND claimed_by=$me
    end

    Task-->>W: complete (after 2min)
    W->>DB: mark complete
```

## 6. Misfire detection on startup

```mermaid
sequenceDiagram
    autonumber
    participant Sch as Scheduler (just booted)
    participant DB
    Note over DB: jobs with next_fire_at < now() — missed during downtime

    Sch->>DB: SELECT due jobs
    loop for each
      alt misfire = IGNORE
        Sch->>DB: UPDATE next_fire_at = trigger.next(now())
      else misfire = RUN_NOW
        Sch->>DB: leave next_fire_at = now() so the next claim picks it up once
      else misfire = RUN_ALL
        Note over Sch: enqueue one execution per missed instance (capped)
      end
    end
```

## 7. Cancel + DLQ

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin
    participant Sch
    participant DB
    participant DLQ

    Adm->>Sch: cancel(jobId)
    Sch->>DB: UPDATE state='CANCELLED' WHERE id=$ AND state IN ('SCHEDULED','FAILED')

    Note over Sch: separate DLQ trigger
    Sch->>DB: SELECT WHERE attempt > retry_max AND state='FAILED'
    Sch->>DLQ: INSERT job + last_error
    Sch->>DB: UPDATE state='DLQ'
```

## Output

```
In-process:  schedule → DelayQueue tick → execute → reschedule via trigger
Failure:     retry with backoff; DLQ on exhaustion
Distributed: SELECT FOR UPDATE SKIP LOCKED claim; lease + heartbeat;
             expired lease → re-claim
Misfire:     IGNORE / RUN_NOW / RUN_ALL on detection
Idempotency: tasks must be idempotent (we provide a key per attempt)
```
