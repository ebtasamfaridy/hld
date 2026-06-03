# 03 · Task Scheduler — High-Level Design

## In-process architecture

```mermaid
flowchart LR
    App[Application] -->|"schedule(task, trigger)"| Sch[Scheduler]
    Sch -- compute next --> Trg[Trigger]
    Sch -- enqueue --> DQ[DelayQueue<br/>min-heap by next fire time]
    Sch -- ticker thread --> DQ
    DQ -- due item --> EX[Executor pool]
    EX -- execute --> App
    EX -- on failure --> Retry[RetryPolicy]
    Retry -- reschedule --> Sch
```

The in-process scheduler is essentially `ScheduledThreadPoolExecutor` with first-class `Trigger` abstraction.

## Distributed architecture

```mermaid
flowchart TB
    Adm[Admin / App] -- create job --> API[Scheduler API]
    API -- INSERT --> DB[(jobs<br/>executions<br/>dlq)]

    subgraph "Worker pool"
      W1[Worker 1] -- poll/claim --> DB
      W2[Worker 2] --> DB
      W3[Worker 3] --> DB
    end

    W1 -- exec --> Tgt[Task code]
    W1 -- mark complete / failed --> DB
    DB -- failed > maxAttempts --> DLQ[(Dead-letter queue)]

    Notify[LISTEN/NOTIFY or pub/sub] -.- DB
    W1 -.- Notify
    W2 -.- Notify
```

## Roles

| Component | Responsibility |
| --- | --- |
| **Scheduler API** | Register / cancel / pause jobs |
| **Job store** | Persist job definitions + executions; source of truth |
| **Worker** | Polls store, claims due jobs, executes, reports outcome |
| **Coordinator** (V2) | Optional leader-only role for housekeeping (misfire detection) |
| **DLQ** | Permanently failing jobs land here for human inspection |

## Hot paths

### In-process: tick → execute

```mermaid
sequenceDiagram
    autonumber
    participant T as Ticker
    participant DQ as DelayQueue
    participant Pool as ExecutorPool
    participant Task

    T->>DQ: take()  (blocks until due)
    DQ-->>T: ScheduledItem
    T->>Pool: submit(task)
    Pool->>Task: run
    Task-->>Pool: complete | exception
    alt success
      T->>T: schedule next fire from trigger
    else failure
      T->>T: retry per backoff or → DLQ
    end
```

### Distributed: claim + execute

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker
    participant DB as Postgres jobs

    W->>DB: BEGIN
    W->>DB: SELECT id FROM jobs<br/>WHERE next_fire <= now()<br/>  AND state='SCHEDULED'<br/>ORDER BY next_fire LIMIT 10<br/>FOR UPDATE SKIP LOCKED
    DB-->>W: [id1, id2, ...]
    W->>DB: UPDATE jobs SET state='CLAIMED', claimed_by=$me, lease_until=now()+30s WHERE id IN (...)
    W->>DB: COMMIT
    loop each job
      W->>W: execute
      W->>DB: UPDATE jobs SET state='SUCCEEDED', last_run=now(), next_fire=trigger.next() WHERE id=...
      Note over W: or on failure: state=FAILED + retry_count++ + backoff
    end
```

### Visibility timeout (worker crashes)

```mermaid
sequenceDiagram
    autonumber
    participant W1 as Worker A (claimed, then crashed)
    participant DB
    participant W2 as Worker B

    Note over W1: A claimed job at t=0, lease_until=t+30s, then crashed
    W1--xDB: connection lost
    Note over DB: jobs.state='CLAIMED', claimed_by='A', lease_until=30s

    W2->>DB: SELECT WHERE state='CLAIMED' AND lease_until<now() FOR UPDATE SKIP LOCKED
    DB-->>W2: id1
    W2->>DB: UPDATE state='SCHEDULED', claimed_by=NULL  (reset for re-claim)
    W2->>DB: claim cycle picks it up
```

Some implementations skip the explicit "reset" step: the claim query simply selects rows where `state='CLAIMED' AND lease_until<now()` directly.

### Lease extension

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker
    participant DB

    Note over W: long task, lease 30s
    loop every 20s
      W->>DB: UPDATE jobs SET lease_until=now()+30s WHERE id=$ AND claimed_by=$me
    end
    W->>W: task finishes
    W->>DB: UPDATE state='SUCCEEDED'
```

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Worker crash mid-execution | Visibility timeout → another worker re-claims |
| DB transient error | Retry with backoff; don't burn retries |
| Task throws | Retry per policy; DLQ on exhaustion |
| Clock skew across workers | Source of truth is DB time (`now()` server-side) |
| Misfire after planned downtime | Apply misfire policy: `RUN_NOW` typical |
| Many duplicate triggers (network glitch) | Idempotency key persisted; DB unique constraint |
| Long-running task | Lease extension; or move to async pattern (publish event, await callback) |

## Output

```
In-process: DelayQueue + thread pool + Trigger abstraction
Distributed: DB-backed job store; workers claim via FOR UPDATE SKIP LOCKED
Reliability: visibility timeout + lease extension + retry + DLQ
Misfire:    policy at job level (IGNORE / RUN_NOW / RUN_ALL)
```
