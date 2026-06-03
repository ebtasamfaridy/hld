# 02 · Splitwise — Capacity Estimation

## Numbers

```
Total users:             100 M
DAU:                      20 M
Expenses/DAU/day:           2
Expenses/day:             40 M
Settlements/day:           5 M
Avg participants/expense:  4
Avg currency types/user:   1.2
```

## Expense write QPS

```
40 M / 86 400 ≈ 460 RPS avg
peak factor 5× (weekend trips, group dinners) → ~2.3 K RPS sustained
event peaks (vacation, festivals): ~5 K RPS
```

Per expense:
- 1 expense row.
- N participant rows (avg 4).
- 1 audit row.
- 1 outbox event row.

Total ~7 row writes per expense → ~35 K writes/sec at peak.

Postgres can do 10–20 K writes/sec on a beefy node; we **partition** by `group_id` (or by month) and shard if needed.

## Read QPS

Balances queries are heavy:
- Per-friend balances.
- Per-group balances.
- Activity feed.

```
DAU × ~10 reads = 200 M reads/day
Peak: ~12 K RPS
```

Most of these are cacheable (Redis). Cache invalidation on each new expense in the affected groups/users.

## Storage

```
Per expense (with participants): ~500 B
Per day:    40 M × 500 B = 20 GB
Per 5 yr:   ~36 TB

Settlements: smaller
Audit:       2× expense
```

Total ~70 TB for 5 years. Partition by month; archive cold partitions to S3.

## Bandwidth

Modest. Most data is text. ~50 MB/s peak.

## Concurrency hot points

| Hot point | Why | Solution |
| --- | --- | --- |
| Group balance update | Many concurrent expenses | Maintain via event log, not inline |
| Friend balance | Same | Recompute or maintain |
| Idempotent expense create | Duplicate submits | UNIQUE on idempotency_key |

---

## Output

```
Expense writes:   2.3 K RPS sustained, 5 K peaks
Read RPS:         12 K (most cached)
Storage:          ~70 TB / 5 yr
Bandwidth:        ~50 MB/s peak
```

These force:
- Postgres partitioned by month + sharded by group_id.
- Redis cache for balances.
- Outbox for events.
- Activity feed via Kafka + ES (eventual consistency).
