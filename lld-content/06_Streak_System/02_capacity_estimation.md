# 02 · Streak System — Capacity Estimation

## Numbers

```
Total users:                    100 M
DAU:                              30 M
Sessions per DAU per day:          5    (multiple opens)
Episodes played per DAU/day:       3
Activity events per DAU/day:       8    (sessions + plays + heartbeats)
```

## Activity-event ingest QPS

```
30 M × 8 = 240 M events/day
240 M / 86 400 ≈ 2 800 RPS average
peak factor 5×                ≈ 14 K RPS sustained
flash peak (drive home, evening commute) ≈ 50 K RPS
```

These hit our `POST /activity` endpoint (or Kafka topic).

**But — most of these events do NOT need to write to the streak DB.**

> Once a user has a `daily_activity` row for today, every subsequent event in the same day is a **no-op** to the streak state. We can short-circuit at the cache layer.

So the **effective write rate** to the durable store is:

```
30 M unique (user, day, type) rows / day
≈ 350 writes/sec average, 1.5 K/sec peak
```

Trivial for one Postgres node. The cost is in the **ingest fan-in**, not the write.

## Read QPS

The streak number appears on the home screen — high read volume.

```
30 M DAU × ~3 home opens/day = 90 M reads/day
≈ 1 K RPS average, 5 K RPS peak
```

Cache-friendly. Almost all served from Redis.

Calendar reads are rarer (user opens calendar tab):
```
~5 M calendar opens/day → ~60 RPS average, ~300 RPS peak
```

Each calendar read returns ~30 day-rows. Easily indexed.

## Storage

```
daily_activity rows: 30 M × 365 days × 5 yr = ~55 B rows
Per row: ~80 B (user_id, date, type, count, first_at, last_at)
Total:   ~4.4 TB
```

This dominates. We **partition `daily_activity` by month** and archive partitions older than ~13 months to cold storage (S3 + Athena for ad-hoc).

```
streak_state rows:   100 M × ~150 B = ~15 GB    (one row per user per type)
milestones rows:     ~few × users × 60 B = ~10 GB
```

These are tiny.

## Cache sizing

| Cache | Key | Value | Size |
| --- | --- | --- | --- |
| Streak state | `streak:{user}:{type}` | current, longest, last_active_day, tz | 30 M DAU × 100 B ≈ 3 GB |
| Today-activity bitmap | `today:{date}` Redis HLL or set | bit per user | ~4 MB / day |
| Active streak type | `admin:active_type` | enum | tiny, replicated |
| Calendar month | `cal:{user}:{ym}:{type}` | 30-bit string | optional; cheap to compute |

Total Redis footprint < 10 GB. Easy.

## Bandwidth

Ingest dominates:
```
50 K RPS × 200 B (event JSON) = 10 MB/s peak
```

Trivial.

## Why these numbers force the design

1. **Event ingest must be cheap and idempotent.** 240 M events/day, but only 30 M unique (user, day) rows are interesting. We cache the "user already counted today" bit and short-circuit ~88 % of writes.

2. **Streak state is per-(user, type)**, ~100 B, mutated O(1) per user per day. Postgres handles it; sharding only required at 10× growth.

3. **Daily activity log** is the largest table. Partition by month, archive old partitions. Calendar reads always hit a small partition (current month + maybe previous).

4. **Reads are cache-first.** Streak number on home → Redis. Cache TTL bounded by *until end of user's local day*, after which we recompute.

5. **No real-time crons needed for streak break.** Streak break is implicit: if `last_active_day < today_user_tz - 1`, the streak is broken. We compute this on the fly during read. No nightly job to "expire" streaks. (We *do* run a daily metrics job for analytics.)

## Output

```
Activity ingest:   14 K RPS sustained, 50 K RPS peak
Effective writes:  1.5 K RPS peak (after dedup cache)
Streak reads:      5 K RPS peak (95 %+ cache hits)
Calendar reads:    300 RPS peak
Storage:           4.4 TB / 5 yr (mostly daily_activity, partitioned)
Cache:             < 10 GB Redis
```

The system is **read-heavy with cheap writes after dedup** — perfect candidate for the architecture in `03_hld.md`.
