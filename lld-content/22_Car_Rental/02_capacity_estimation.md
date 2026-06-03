# 02 · Car Rental — Capacity Estimation

## Scale assumptions (target: top-3 Indian self-drive operator)

| Dimension | V1 number | Source |
| --- | --- | --- |
| Cities | 30 | Top metro + tier-2 |
| Vehicles total | 50,000 | Mix of compact / sedan / SUV |
| Registered users | 5,000,000 | |
| MAU | 800,000 | 16% of registered |
| Active concurrent trips | 5,000 (peak weekend) | 10% of fleet in flight |
| Reservations / day (avg) | 25,000 | 0.5 trips/car/day avg utilisation |
| Reservations / day (peak holiday) | 200,000 | 4 trips/car/day peak |
| Search queries / day | 5,000,000 | ~6 queries per active user |

---

## Throughput math

- Search queries: 5M/day → **60 QPS sustained**, **5K QPS at peak hours** (Friday evening).
- Reservation creates: 200K/day peak → **2.3 QPS sustained**, **200 QPS** at sale-launch peaks (long-weekend openings).
- Trip status updates: 5K active trips × GPS every 30s = **170 QPS sustained writes**.
- Unlock / lock commands: ~50 QPS at pickup peaks.
- Damage claim creation: ~500/day = **0.006 QPS** (rare; not a hot path).

| Hot operation | Sustained QPS | Peak QPS | Critical? |
| --- | --- | --- | --- |
| Search (geo + time filter) | 60 | 5,000 | Latency-critical, eventual OK |
| Vehicle detail view | 100 | 1,000 | CDN-cached |
| Reservation create | 2 | 200 | **Strongly consistent + low-latency** |
| Trip GPS ingest | 170 | 1,000 | High throughput, lossy-OK |
| Unlock | 5 | 50 | Critical UX, but rare |
| Final fare compute (return) | 2 | 100 | Strongly consistent |
| Damage charge | 0.006 | 1 | Strongly consistent + idempotent |

---

## Storage estimates

### Catalog

- 50K vehicles × ~5 KB metadata (photos URLs, specs, current fuel, last loc) = **250 MB**. Tiny.
- 100 VehicleModel entries × ~10 KB = **1 MB**. Trivial.

### Time-slot inventory

- **The dominant storage in this system.**
- 50K vehicles × 24 h/day × 90 days lookahead = **108 M slots**.
- Each slot: ~50 bytes (vehicle_id, hour_bucket, status, optional reservation_id, version).
- Total: **5.4 GB** — fits comfortably in Postgres.
- Hot slots (today + tomorrow): ~2.4 M rows; cache in Redis for read-heavy search filter.

### Reservations

- 200K/day × 365 days × 5 years = **365 M reservations**.
- Each: ~2 KB (header + window + deposit + idempotency).
- Total: **730 GB / 5 yr**. Partition by `created_at` monthly.

### Trips

- Same count as reservations roughly (1:1 with CONFIRMED that became ACTIVE).
- Plus GPS breadcrumbs: 5K active trips × 60-hour avg trip × 120 pings/hr = **36 M GPS rows / day**.
- 1 KB each = **36 GB / day**, **13 TB / year**. → Tiered storage: hot (last 7 days) in Postgres; cold to S3 + Athena for analytics.

### Damage claims

- 500/day × 365 × 5 = **1 M claims / 5 yr** × 5 KB = **5 GB**. Tiny.

### Photos / videos

- Pre/post photos: 4 photos × 200 KB × 25K reservations/day = **20 GB / day**. CDN with origin in S3.

---

## Bandwidth

- Search bandwidth peaks at 5 K QPS × 5 KB = **25 MB/s out** (mostly served by Redis + CDN).
- GPS ingest: 1K writes/sec × 1 KB = **1 MB/s** in. Trivial.
- Photo upload bandwidth (peak): **50 MB/s** during peak pickup/return windows.

---

## Hotspots & bottlenecks

| Bottleneck | Reason | Mitigation |
| --- | --- | --- |
| **Time-slot row contention** | Hot vehicle on a holiday weekend = multiple writes on same slot row | Conditional INSERT with `ON CONFLICT DO NOTHING`; `(vehicle_id, hour_bucket)` PK is the natural lock |
| **Geo-search QPS at peak** | Friday-evening ramp; 5K QPS searching same city | Pre-computed availability buckets per (city, time_window); refresh every 30 s |
| **Payment gateway latency** | 500–2000 ms per auth call | Async pre-auth where possible; optimistic UI; circuit breaker |
| **GPS ingest spikes** | 5K active trips simultaneously | Kafka partition by trip_id; backed by columnar store |
| **IoT unlock latency** | Cellular link to vehicle module | 5-second timeout, retry once; fall back to ops support |
| **Damage claim charge race** | User books a new trip while damage charge is in flight | Block new bookings while there's an unresolved DUNNING flag on the user |

---

## Sharding strategy

| Data | Sharding key | Why |
| --- | --- | --- |
| Vehicles & catalog | city_id | Most queries are city-scoped |
| Slot inventory | vehicle_id (consistent hash) | Spreads write load across shards |
| Reservations | user_id | "My bookings" is the dominant read |
| Trips | user_id (with hot trips co-located in Redis) | Same as reservations |
| GPS breadcrumbs | trip_id | Sequential writes per trip |
| Damage claims | trip_id | Co-located with trip |

---

## Read-vs-write profile

```
Reads >> writes for catalog/search (~100:1)
Reads ≈ writes for slot inventory (search reads + booking writes)
Writes dominate trips during active hours (GPS ingest)
Money-bearing writes are slow but rare (~200 QPS peak)
```

---

## Output

```
Fleet:           50K vehicles, 30 cities, 5M users
Hot reads:       search (5K QPS peak)
Hot writes:      slot reservation (200 QPS peak), GPS ingest (1K QPS sustained)
Storage:         108M slots ≈ 5GB; 365M reservations / 5 yr ≈ 730GB; GPS 13 TB/yr (tiered)
Sharding:        vehicles by city; slots by vehicle; reservations by user; GPS by trip
Bottleneck1:     hot-vehicle slot contention → ON CONFLICT DO NOTHING
Bottleneck2:     payment gateway p99 latency → off the hot path
Bottleneck3:     GPS ingest → Kafka with cold tiering
```
