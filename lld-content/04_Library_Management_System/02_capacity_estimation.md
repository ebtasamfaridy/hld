# 02 · Library — Capacity Estimation

## Numbers

```
Books (titles):              5 M unique
Avg copies per book:           3
Total copies:                ~15 M
Branches:                     100
Members:                    100 K
DAU:                         10 K
Borrows/day:                 50 K (peak factor 4× for school terms)
Reservations/day:            10 K
Returns/day:                 50 K
Fine payments/day:            5 K
```

## Borrow RPS

```
50 K / 86 400 ≈ 0.6 RPS avg
peak: ~5 RPS sustained, ~50 RPS at counter rush hour
```

Per borrow:
- 1 SELECT copy (or atomic UPDATE)
- 1 INSERT loan
- 1 update member (loan count)
- audit row

≈ 4 writes per borrow → 200 writes/sec peak. Trivial.

## Search RPS

10 K DAU × 5 searches/day × 4× peak = ~10 RPS at peak. Trivial.

But cold-start of catalog or popular queries — Elasticsearch helps.

## Storage

```
Books metadata: 5M × 2 KB = 10 GB
Copies:         15M × 200 B = 3 GB
Members:        100K × 1 KB = 100 MB
Loans (5 yr):   50K × 365 × 5 × 500 B = ~45 GB
Audit (5 yr):   ~5× loans = 225 GB
Fines:          ~10 GB
```

Total ~300 GB hot + 1 TB cold over 10 yr. Single Postgres easily.

## Concurrency hot points

| Hot point | Why | Solution |
| --- | --- | --- |
| Last copy of a popular book | Many concurrent borrow requests | DB CAS atomic |
| Reservation queue head | Multiple events trigger promotion | Single-writer worker |
| Daily fine cron | All overdue loans | Batch job |

---

## Output

A library system is **not big-data**. Its constraints are:
- Strong consistency on copy state (no double-borrow).
- Concurrency on individual copies and reservation queues.
- Correctness on fines.
- Smooth daily operations (reminders, fine cron).

Scale is modest. We don't need ES strictly, but it's nice for fuzzy search. A single Postgres handles the whole system with comfortable headroom.
