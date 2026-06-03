# 02 · Ride Booking — Capacity Estimation

## Numbers

```
Total users:            100 M
DAU:                     20 M
Rides/DAU/day:           0.5
Rides/day:               10 M
Avg ride duration:       15 min
Avg distance:            7 km
Active drivers:           1 M (instant peak: ~200 K)
```

## Booking write QPS

```
10 M / 86 400 ≈ 116 RPS  average
Peak factor 5× (office hours, airport peaks) → ~600 RPS
```

Each booking writes:
- 1 ride row
- ~6 status updates (REQUESTED → MATCHED → ARRIVING → STARTED → COMPLETED + 1 payment)
- 1 payment row
- 1 audit row per transition (×6)

Total ~14 row writes per ride → **~8 K writes/sec at peak** for ride-flow tables. Postgres handles this; we'll partition rides by month.

## Driver location stream

```
200 K active drivers × (1 update / 4 s) = 50 K writes/sec
≈ 4.3 B writes/day
```

NOT in main DB. Goes to:
- Redis Geo (`GEOADD`, sub-ms reads).
- Kafka (`driver-locations` topic).
- Downsampled to S3 (1/min for trip replay).

## Geo search QPS

Match engine query: "find idle drivers within 3 km of point P, ride type X."

```
Bookings/sec at peak: 600
Each does ~1 geo search (with retries, ~1.5)  → 1 K geo searches/sec
```

Redis Geo handles this trivially (~10 K ops/sec/node).

But during surge spikes, riders also poll surge factor (~5 K reads/sec). That's still cache-friendly.

## Read QPS

```
Rider session: ~30 reads (home, search, fare, track, history)
10 M × 30 = 300 M reads/day
Peak: ~17 K RPS
```

Largely cached or served from Redis.

## Storage

```
Per ride: ~3 KB (request + path snapshot + price breakdown + driver/rider snapshots + audit)
Per day:  10 M × 3 KB = 30 GB
Per 5 yr: 30 GB × 365 × 5 × 1.5 = 80 TB
```

Partition by month, archive after 1 year, drop after 7 (legal retention varies).

## Driver location archive

```
50 K writes/sec × 60 B = 3 MB/sec
Per day: ~250 GB
Downsampled (1/min): 10 GB/day
```

Use Cassandra (`(driver_id, ts)` key) or stash in S3 partitioned by date.

## Bandwidth

| Stream | Bandwidth |
| --- | --- |
| Driver locations | 50 K × 60 B = 3 MB/s |
| Rider tracking pushes (peak 100 K active rides) | 25 MB/s = 200 Mbps |
| Booking API JSON | 600 RPS × 5 KB = 3 MB/s |
| Surge factor cache | trivial |

---

## What the numbers force

1. Driver location **must** be in Redis Geo — Postgres can't.
2. Tracking must use **WebSocket fanout** — not 0.1 Hz polling.
3. Match engine is the latency-critical path — Redis Geo is non-negotiable.
4. Ride history is large — partition + archive.
5. Surge calc is per-zone-per-minute — Redis hash keyed by geohash.

---

## Output

```
Booking writes:    600 RPS peak (~8K w/s including audit)
Read RPS:          17 K peak
Driver locations:  50 K writes/sec (Redis + Kafka)
Storage:           80 TB / 5 yr (partition + archive)
Bandwidth:         200 Mbps tracking, 25 Mbps location
```
