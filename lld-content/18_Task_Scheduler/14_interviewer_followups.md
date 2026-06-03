# 14 · Task Scheduler — Interviewer Follow-ups

## Q1. "Implement an in-process scheduler that supports fixed-rate, fixed-delay, and one-shot."

Use `DelayQueue<Delayed>`. Each scheduled item carries `(jobId, fireAt)`. The ticker thread does `take()` (blocks until head is due), submits the task to a worker pool, then computes the next fire time via the trigger and re-enqueues.

That's the whole thing in 50 lines of Java.

---

## Q2. "What's the difference between fixed-rate and fixed-delay?"

- **Fixed rate**: target fire times are `start, start+T, start+2T, ...`. If task #2 takes longer than T, task #3 still aims for `start+2T` (i.e., it fires immediately when task #2 finishes).
- **Fixed delay**: each fire is `previousFinish + delay`. Slow tasks shift everyone later.

Fixed-rate is for "I want this to run every minute, sharp." Fixed-delay is for "I want at least 1 minute between runs."

---

## Q3. "How do you parse a cron expression?"

5 fields: minute (0–59), hour (0–23), day-of-month (1–31), month (1–12), day-of-week (0–6).

Each field is either:
- `*` (any),
- comma list `1,5,10`,
- range `1-5`,
- step `*/5` or `1-30/5`,
- single value.

To compute next-fire-after-T:
1. Start at `T+1 second`.
2. For each field (minute → hour → day → month), advance to the smallest valid value ≥ current.
3. If a field "rolled over", reset all lower fields to their first valid value.
4. Repeat until all fields are valid; cap iterations to detect impossible expressions.

For interview, write a simple parser supporting `*`, `*/N`, lists, ranges. Use `cron-utils` in production.

---

## Q4. "What's the data structure for in-process scheduling?"

Min-heap (or `DelayQueue`) ordered by `nextFireTime`. `O(log n)` insert, `O(1)` peek, `O(log n)` remove.

A sorted list would be `O(n)` insert. A hashmap doesn't track ordering. A min-heap is the right answer.

---

## Q5. "Multiple workers — how do they not run the same job twice?"

`SELECT FOR UPDATE SKIP LOCKED`:
```sql
SELECT id FROM jobs
WHERE state='SCHEDULED' AND next_fire_at <= now()
ORDER BY next_fire_at LIMIT 10
FOR UPDATE SKIP LOCKED;
```

Each transaction sees rows the others haven't locked. The matching `UPDATE` flips the state to `CLAIMED` atomically.

`SKIP LOCKED` is critical: without it, workers serialize.

---

## Q6. "Worker crashes mid-execution. Now what?"

`lease_until` set on claim (e.g., `now + 30s`). When the worker dies, the lease is no longer extended. Another worker's claim query includes `state='CLAIMED' AND lease_until < now()`, picks it up, and re-runs.

The task **must be idempotent** — at-least-once delivery is the contract.

---

## Q7. "Long-running task — how do you keep its lease alive?"

A heartbeat thread on the worker periodically does:
```sql
UPDATE jobs SET lease_until = now() + 30s
WHERE id = $jobId AND claimed_by = $workerId;
```

If the heartbeat fails (DB issue, worker dying), the lease eventually expires and another worker takes over.

Note the `claimed_by = $workerId` guard: if our lease was already taken by another worker, we silently fail to extend. Consistent.

---

## Q8. "How would you handle a deluge of misfires after a 4-hour outage?"

Misfire policy:
- `IGNORE`: skip; jump to next future fire. Safest.
- `RUN_NOW`: run once to catch up; useful for "make sure it's been done at least once".
- `RUN_ALL`: run every missed instance. Dangerous: can flood the system.

Default to `RUN_NOW`. Critical jobs (e.g., billing) might use `RUN_ALL` with rate-limited replay.

---

## Q9. "How do retries with exponential backoff work?"

Attempt 1 fails → schedule next at `now + 5s`.
Attempt 2 fails → next at `now + 10s`.
Attempt 3 fails → `now + 20s`.
And so on, capped at e.g. 5 minutes.

Add jitter (±25 %) to prevent thundering herd. After `maxAttempts`, move to DLQ.

---

## Q10. "Idempotency — what does it actually mean here?"

The framework promises **at-least-once** execution. It might run the task twice (worker crashed after running but before marking complete).

The task code must produce the same effect either way. Common approaches:
- **Database upsert**: INSERT … ON CONFLICT DO UPDATE. Re-running with the same key is no-op.
- **Idempotency keys**: each attempt has a unique key (`jobId:scheduledFor:attempt`). External APIs that support `Idempotency-Key` headers (Stripe, etc.) reject duplicates.
- **State machine**: check current state before acting; only act if the work hasn't been done.

The framework provides the key; the task uses it.

---

## Q11. "Pause/resume vs cancel — semantics?"

- **Pause**: don't fire, but keep the job. State `PAUSED`. Future `nextFireTime` not computed until `resume`.
- **Resume**: re-compute `nextFireTime` from now; back to `SCHEDULED`.
- **Cancel**: terminal. State `CANCELLED`. No future fires; history kept.

A paused job that's currently running continues to completion; only future fires are paused.

---

## Q12. "Many jobs scheduled at midnight — how do you handle the burst?"

Workers grab batches via `LIMIT 10` claim. The first second sees 100 workers × 10 = 1000 jobs claimed. Most complete quickly. Burst drains in seconds.

If you need them to fire in a specific order or concurrently, use priorities. If you need them all done in the first 100 ms, your scheduler isn't the answer — that's a real-time system.

---

## Q13. "How does pause persist across a worker restart?"

It's in the DB: `state='PAUSED'`. On startup, the scheduler loads jobs and sees `PAUSED` — doesn't fire them. A `resume` flips state and recomputes `nextFireTime`.

This is why distributed mode needs a durable store: the in-process variant loses state on restart.

---

## Q14. "Two clients racing to update the same job's trigger?"

Optimistic concurrency:
```sql
UPDATE jobs SET trigger=$new, version=version+1
WHERE id=$id AND version=$expected;
```

If 0 rows updated → 409 Conflict. Client refreshes, edits, retries. No DB-level locks; lock-free protocol.

---

## Q15. "What's the hardest correctness bug?"

Lease expiry race:
1. Worker A claims at t=0, lease = 30s.
2. Worker A's task takes 32 s (slow DB call).
3. At t=30, lease expires. Worker B claims and runs the task.
4. At t=32, Worker A finishes and tries to mark complete.

Without the `claimed_by = $workerId` guard, Worker A would mark Worker B's claim complete. Now we have two parallel runs and confused state.

The guard ensures Worker A's update affects 0 rows. Worker A discards its result; Worker B finishes normally.

The lesson: **always check ownership in mark-complete updates.**

---

## Output

```
Drilled:
- DelayQueue / min-heap for in-process
- Fixed-rate vs fixed-delay vs one-shot semantics
- Cron parsing (5 fields + special tokens; advance + reset algorithm)
- FOR UPDATE SKIP LOCKED for distributed claim
- Visibility timeout for crash recovery
- Lease extension for long tasks
- Misfire policy (IGNORE / RUN_NOW / RUN_ALL)
- Exponential backoff with jitter
- At-least-once + idempotency contract
- Pause vs cancel semantics
- Burst handling via batched claims
- Pause persistence in distributed mode
- Optimistic concurrency on admin edits
- Lease expiry race + ownership-guarded updates
```
