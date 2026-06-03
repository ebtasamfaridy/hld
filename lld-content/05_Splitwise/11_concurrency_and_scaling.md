# 11 · Splitwise — Concurrency & Scaling

## Race conditions

| # | Race | Solution |
| --- | --- | --- |
| 1 | Two clients submit duplicate expenses | Idempotency key UNIQUE |
| 2 | Two users edit same expense | Optimistic version on expense |
| 3 | Two parallel expense events update same balance | Single-writer per Kafka partition |
| 4 | Edit + delete collide | Optimistic version |
| 5 | Settlement + concurrent expense addition | Both events ordered through Kafka |
| 6 | Group close + new expense | DB constraint + group state guard |
| 7 | Member remove + outstanding balance | Pre-check + reject |
| 8 | Currency changes mid-edit | Currency is immutable on expense |

---

## 1. Idempotent expense creation

```sql
ALTER TABLE expenses ADD COLUMN idempotency_key VARCHAR(80) UNIQUE;
```

Server lookup before insert; UNIQUE catches the rare race.

---

## 2. Optimistic locking

```sql
UPDATE expenses SET ..., version=version+1 WHERE id=? AND version=?;
```

If 0 rows → conflict. Caller refreshes and retries.

---

## 3. Event-driven balance updates

Balance Service consumes `ExpenseCreated` / `Edited` / `Deleted` and `SettlementRecorded` events.

### Why not update balance in the same transaction as the expense?

- The balance is a **derived view**. Mixing it with the source of truth couples reads to writes.
- Pair balance updates touch many rows (one per pair); the transaction would balloon.
- We get free per-event audit and replay.
- Eventual consistency is acceptable for balance display (~ms lag).

### Single-writer per partition

Kafka partitions by `group_id` (or `min(userA, userB)` hash for non-group). Within a partition, events are ordered. Each partition has one consumer thread that updates balances sequentially.

This avoids race conditions on balance rows without locks.

### Idempotency on consumers

Each event has `event_id`. We track `last_event_id` per pair_balance row:

```sql
UPDATE pair_balances SET net_amount = net_amount + $delta, last_event_id = $eid
WHERE user_a=? AND user_b=? AND group_id=? AND currency=? AND last_event_id < $eid;
```

If this event was already applied (re-delivered after consumer restart), `last_event_id` is already >= eid, the update affects 0 rows. Safe.

---

## 4. Edit / delete carry old + new

`ExpenseEdited` event has both old and new shares:

```json
{
  "expense_id": "e_1",
  "old_shares": [...],
  "new_shares": [...],
  "old_payers": [...],
  "new_payers": [...]
}
```

Balance Service reverses the old deltas and applies the new — deterministic, no need to query historic state.

`ExpenseDeleted` carries the snapshot of shares so balances can reverse.

---

## 5. Group close concurrency

```sql
UPDATE groups SET closed=TRUE WHERE id=$g AND closed=FALSE
  AND NOT EXISTS (SELECT 1 FROM pair_balances WHERE group_id=$g AND net_amount != 0);
```

If 0 rows → either already closed or has open balances.

But: an expense being created concurrently could create non-zero balance after the close check. Defense:

```sql
INSERT INTO expenses (...)
WHERE NOT EXISTS (SELECT 1 FROM groups WHERE id=$g AND closed=TRUE);
```

Pseudo SQL — use a SELECT-then-INSERT in a serializable transaction or a DB-level constraint via trigger. Simpler: have a `groups.closed` check inside the expense INSERT path with row lock on the groups row.

---

## 6. Concurrency for debt simplification

Debt simplification is a **read** operation. We:

1. Snapshot all pair balances for the group (per currency).
2. Compute net per user.
3. Run min-cash-flow.
4. Return.

If new expenses come in mid-computation, the snapshot may be slightly stale. That's acceptable — we tell the user "as of {timestamp}" or recompute on next view.

We can also cache simplified results for 60 seconds.

---

## 7. Floating-point and rounding

Always work in **integer cents/paise** internally. Use `BigDecimal` for arithmetic.

When splitting equally:

```
total = 100.00
n = 3
share = 33.33   each, total = 99.99 -- short by 0.01
```

We assign the missing 0.01 to the first participant in canonical order:

```
participant 1: 33.34
participant 2: 33.33
participant 3: 33.33
```

Deterministic order ensures balance invariants hold (sum == total exactly).

---

## 8. Multi-currency invariant

Each pair_balance row is per (userA, userB, group, currency). We **never** mix currencies. A debt of $100 stays $100 forever; conversion is at display time only.

This means a group with members from different countries can have multiple balance rows per pair. The "net" is per currency.

---

## 9. Scaling

### Vertical → horizontal

- Stateless services scale on K8s.
- Postgres partitioned by month for `expenses`, `expense_audits`, `settlements`.
- Sharded by `group_id` (or `min_user_id`) when write rate exceeds 10K/sec.
- Redis Cluster for balance cache.

### Read path

- Balance reads are 90%+ cache hits.
- Activity feed via separate ES / Cassandra cluster.

### Search (V2)

For "find expenses involving X with amount > Y" — Elasticsearch with CDC.

### Multi-region

Splitwise is global. Data should follow users' primary region:
- US users in us-east shard.
- IN users in ap-south shard.
- Cross-region groups are rare; if both users are in different regions, choose one or run a special "global" shard.

We accept some compromises for cross-region groups (slightly higher latency).

---

## 10. Failure modes

| Failure | Mitigation |
| --- | --- |
| Balance service lag | Stale balance shown; auto-refreshed |
| Kafka outage | Outbox holds events |
| DB primary failover | App retries idempotent commands |
| Redis down | Recompute from snapshot + recent events |
| Reconciliation finds drift | Alert SRE; full recompute from event log |

---

## 11. Stress test for debt simplification

```
random N (10..100)
randomly create M (10..1000) expenses among them
compute pair_balances
simplify
verify:
  - sum of transfers from A == net debt of A
  - no transfer exceeds available debt
  - count of transfers ≤ N - 1
```

Run on every deploy.

---

## Summary

- Idempotency on expense create.
- Optimistic versioning for edits.
- Event-driven balance updates with single-writer per partition + last_event_id idempotency.
- Edits carry before/after for deterministic reversal.
- Currencies kept separate.
- Balances are derived; recompute is the safety net.
