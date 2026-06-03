# 02 · BookMyShow — Capacity Estimation

## Scale numbers

```
Cities:              200
Theatres:            ~10 K
Screens:             ~50 K
Shows / day:         ~150 K
Seats / show:        ~300
Bookings / day:      ~5 M
Peak bookings/sec:   ~5 K (big release evening)
Catalog reads:       100× the writes — ~500 K RPS read peak
```

## Hot paths

| Op | RPS peak | Latency target |
| --- | --- | --- |
| Browse city → movies | 50 K | < 100 ms (cacheable) |
| Get show seat layout | 30 K | < 200 ms |
| **Hold seats** (write) | 5 K | < 200 ms p99 |
| Confirm (after pay) | 5 K | < 1 s p99 (gateway hop) |

The overwhelming majority of traffic is **read**. Writes spike when shows go live.

## Storage

```
Show metadata:           150 K shows × ~2 KB = ~300 MB (cacheable in full)
Show seats (current):    150 K × 300 = 45 M rows (1 KB each) ≈ 45 GB hot
Bookings (5 yrs):        5 M/day × 1 KB × 365 × 5 = ~9 TB → partitioned
Payments:                similar to bookings
```

## What forces the design

1. **Catalog (movies / shows / seat layouts) is read-heavy** → CDN + edge cache.
2. **Seat inventory is write-hot at show open** → Redis for holds + Postgres transactional confirm.
3. **No double-booking** is the hardest invariant; everything else falls out from it.
4. **TTL on holds** must not require a daily cron — Redis TTL handles it.
5. **Notifications and post-confirm side effects** go through Kafka — async.

## Hot spots

A single show going live (Friday 6 PM, blockbuster): 100 K users hit "Get seat layout" simultaneously. Mitigations:
- CDN-cached layout (TTL 30s), invalidated when seats change.
- Show inventory keyed by `showId` in Redis; **fan-in protection** via single-flight (only one DB query per layout per N ms across the cluster).

## Hold throughput (the critical write)

5 K seats/sec held. Each hold is a Redis `SET NX` plus a Postgres write (audit). Redis can do 100 K/sec; Postgres single-shard can do 10 K/sec writes — ample.

## Output

```
Reads:        500 K RPS peak (mostly browse + show layout) — cacheable
Writes:       5 K RPS peak (holds) — Redis SETNX
Confirms:     5 K RPS peak (with payment) — Postgres TX
Storage:      ~9 TB bookings over 5 yrs (partitioned)
Show open:    100 K simultaneous viewers — CDN + single-flight
```
