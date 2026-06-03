# 11 · Library — Concurrency & Scaling

## Race conditions

| # | Race | Solution |
| --- | --- | --- |
| 1 | Two members borrow the same copy | DB CAS atomic UPDATE |
| 2 | Member loan limit exceeded by parallel borrows | Atomic UPDATE WHERE active_loan_count < limit |
| 3 | Two workers promote same reservation | SELECT FOR UPDATE SKIP LOCKED |
| 4 | Cron runs twice on same day (double fine) | UNIQUE on (loan_id, accrual_date) |
| 5 | Member retries pay fine | Idempotency key + UNIQUE |
| 6 | Borrow + reservation race for the same copy | RESERVED_HOLD prevents random borrow |
| 7 | Return + lost-report collide | State guard on Loan transitions |
| 8 | Concurrent renew + reservation create | Renew rejected if reservations exist |

---

## Borrow concurrency — deep dive

### Naive (broken)

```sql
SELECT id FROM book_copies WHERE book_id=? AND status='AVAILABLE' LIMIT 1;
-- application: chose copy
UPDATE book_copies SET status='BORROWED' WHERE id=?;
```

Race: two members read the same available copy and both UPDATE. Result: both think they got it, but only one has the loan.

### Correct (CAS)

```sql
UPDATE book_copies SET status='BORROWED', version=version+1
WHERE id=? AND status='AVAILABLE' AND version=?;
```

If 0 rows updated → another transaction got it. The application picks the next available copy and retries (max ~3 times before declaring `NO_COPY_AVAILABLE`).

### Belt-and-suspenders: UNIQUE active loan per copy

```sql
CREATE UNIQUE INDEX uniq_loans_one_active_per_copy
ON loans (copy_id) WHERE status IN ('BORROWED','OVERDUE');
```

If somehow two transactions pass the CAS (impossible with the version column, but defensive), the INSERT loan will fail with a UNIQUE violation. Defense in depth.

---

## Member loan limit

```sql
UPDATE members
SET active_loan_count = active_loan_count + 1, version=version+1
WHERE id=$member AND status='ACTIVE' AND active_loan_count < $maxLimit;
```

If 0 rows → at limit or suspended. We translate to `409 LIMIT_REACHED`. No application-side check-then-update window.

---

## Reservation queue

### Single-writer per book

To promote the head of a queue:

```sql
SELECT id FROM reservations
WHERE book_id=? AND status='QUEUED'
ORDER BY queue_position ASC
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

`SKIP LOCKED` lets multiple workers process **different** books concurrently. For the same book, only one worker holds the lock; others skip.

### Promotion atomicity

```sql
BEGIN;
-- claim head
SELECT id FROM reservations WHERE book_id=? AND status='QUEUED' ORDER BY queue_position ASC FOR UPDATE SKIP LOCKED LIMIT 1;
-- mark a specific copy held
UPDATE book_copies SET status='RESERVED_HOLD', version=version+1 WHERE id=$copy AND status='AVAILABLE' AND version=?;
-- mark reservation ready
UPDATE reservations SET status='READY', ready_at=now(), expires_at=now()+interval '24 hour' WHERE id=$res;
-- insert outbox(ReservationReady)
COMMIT;
```

All-or-nothing. If the copy can't be CAS'd (someone else borrowed at the moment), abort and rely on the next event to retry promotion.

### Holds and renewals

When a member tries to renew a loan, we reject if any QUEUED reservation exists:

```sql
SELECT 1 FROM reservations WHERE book_id=? AND status='QUEUED' LIMIT 1;
-- if found, deny renew
```

Otherwise the queue would never advance.

---

## Fine accrual idempotency

The daily cron must run safely if re-triggered:

```sql
CREATE TABLE fine_accruals (
  loan_id        UUID NOT NULL,
  accrual_date   DATE NOT NULL,
  amount         NUMERIC(10,2) NOT NULL,
  PRIMARY KEY (loan_id, accrual_date)
);

INSERT INTO fine_accruals (loan_id, accrual_date, amount)
SELECT l.id, CURRENT_DATE, $perDay
FROM loans l
WHERE l.status='OVERDUE' AND l.due_date < CURRENT_DATE
ON CONFLICT (loan_id, accrual_date) DO NOTHING;

-- aggregate into fines table
INSERT INTO fines (member_id, loan_id, kind, amount, status)
SELECT l.member_id, l.id, 'LATE',
       (SELECT sum(amount) FROM fine_accruals WHERE loan_id=l.id),
       'OUTSTANDING'
FROM loans l ...
ON CONFLICT (loan_id, kind) WHERE status='OUTSTANDING'
DO UPDATE SET amount = EXCLUDED.amount;
```

Or simpler: keep one row per (loan, kind) and increment its amount. Either works.

---

## Idempotency on borrow

```sql
ALTER TABLE loans ADD COLUMN idempotency_key VARCHAR(80) UNIQUE;
```

Borrow API takes `Idempotency-Key`. Replays return the existing loan.

---

## Reservation expiry cron

Every 5 min:

```sql
SELECT id FROM reservations
WHERE status='READY' AND expires_at < now()
FOR UPDATE SKIP LOCKED;
```

For each:
- `status -> EXPIRED`.
- Release the held copy (`RESERVED_HOLD -> AVAILABLE`).
- Trigger `promoteHeadOfQueue(book)`.

The cascade: expiring one reservation may promote the next, which might also expire — cron processes them safely.

---

## Read replicas

For admin reports and search, route to read replicas. Write traffic stays on primary.

```
write: primary
read (member dashboards): primary (acceptable lag would confuse user)
read (admin reports): replica
read (search): ES (CDC from primary)
```

---

## Scaling the system

The library system isn't huge by web standards. But:

### Modest growth (10 M members)

- Vertical scale primary Postgres.
- Read replicas for reports.
- Add ES for fuzzy search if `pg_trgm` becomes slow.
- Per-region replicas if multi-country.

### Serious growth (national chain, 100 M members)

- Shard by `branch_id` — most queries are per-branch.
- Per-shard reservation queues.
- Cross-shard read replicas for search.
- Member service is per-region.

---

## Failure modes

| Failure | Mitigation |
| --- | --- |
| DB primary failover | Idempotent commands; clients retry |
| Cron skips a day | Backfill: cron picks up from last_processed_date watermark |
| Notification provider down | DLQ retry |
| Payment gateway down | Show fine still outstanding; member retries |
| Reservation worker crashes mid-promote | TX rolls back; next event re-promotes |

---

## Summary

- Borrow uses atomic CAS on copy and member.
- Reservation queue uses `SELECT FOR UPDATE SKIP LOCKED` to allow safe parallelism.
- Daily fine cron is idempotent via UNIQUE constraints.
- All flows are at-most-once via idempotency keys.

The library system is small but every concurrency primitive matters — staff candidates show command of these patterns even at modest scale.
