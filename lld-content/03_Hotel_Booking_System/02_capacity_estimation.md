# 02 · Hotel Booking — Capacity Estimation

## Numbers

```
Active hotels:            500 K
Avg rooms per hotel:        50
Total room-nights/year:   500K × 50 × 365 = ~9 B
Bookings/day:               1 M (avg)
Peak factor:                10× (Black Friday, summer)
Bookings peak:              ~120 RPS sustained, 1.2 K RPS peak
Active users:              50 M
DAU during sale season:    10 M
```

## Bookings RPS

```
1 M bookings / 86 400 ≈ 12 RPS avg
peak factor 10× ≈ 120 RPS sustained
event peak (Black Friday): ~1 K-5 K RPS
```

Each booking writes:
- 1 booking row.
- N room-night rows decremented (1 per night × room count).
- 1 payment row.
- Several audit / event rows.

Total ~10 row writes per booking. **Peak: ~50 K writes/sec under event surges.**

This is past Postgres single-node; we partition by `(hotel_id, date)` or shard by hotel.

## Search RPS

Heavy. For each search:
- 1 ES query.
- ~20 hotel results × availability check.

```
DAU × ~5 searches/user × peak factor = ~50 K searches/sec at peak
```

Each search hits Elasticsearch + Redis (availability cache). Postgres only on cache miss for inventory checks.

## Storage

```
Active bookings (hot, 1 yr ahead): 1M × 365 × 2 KB = ~700 GB
Historical (5 yr): 5 × 1 M × 365 × 2 KB = ~3.5 TB
Inventory rows: 500K hotels × ~10 room types × 730 days = ~3.6 B rows × 50 B = ~180 GB
Hotel metadata: 500K × 5 KB = 2.5 GB
```

Inventory table is the largest hot table. We partition by date.

## Cache sizing

| Cache | Size | TTL |
| --- | --- | --- |
| Hot hotels metadata | 100K × 50 KB = 5 GB | 60 s |
| Availability per popular hotel × next 90 days | 100K × 90 × 100 B = 900 MB | 30 s |
| Search results (top queries) | ~1 GB | 5-30 s |

## Bandwidth

```
Searches: 50K RPS × 30 KB = 1.5 GB/s   ← CDN required
Booking pages: lower
```

## Concurrency hot points

| Hot point | Why | Solution |
| --- | --- | --- |
| Last room on date X | Many concurrent attempts | DB CAS on (hotel,room_type,date) row |
| Hotel marks blocked | Conflicts with existing bookings | Pre-check + reject |
| Modify booking | Changes inventory | Two-phase: release old, reserve new |
| Payment retry | Network retries | Idempotency key + UNIQUE |

## Output

```
Bookings:        120 RPS sustained, 5 K RPS event peaks
Search RPS:      50 K (cached + ES)
Storage:         ~4 TB hot bookings + 200 GB inventory + 1 PB cold archive (10 yr)
Inventory:       3.6 B rows; partition by date
Bandwidth:       1.5 GB/s search (CDN) + 0.2 GB/s booking
```

These force:
- Inventory in partitioned Postgres + Redis cache.
- Search via Elasticsearch.
- Strong consistency on **booking transactions only**; eventual elsewhere.
- Async settlement and notifications.
