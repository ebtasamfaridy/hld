# 02 · Food Delivery — Capacity Estimation

## Numbers we will use

```
Total users:        200 M       (single country e.g. India)
DAU:                30 M        (15% of total)
Orders / DAU:       0.30        (avg)
Orders / day:       9 M
Avg basket size:    3 items
Avg order value:    ₹400
Active restaurants: 1.5 M
Active drivers:     1 M (instantaneous: ~150 K)
```

---

## Write QPS

```
9 M orders / 86 400 sec  ≈ 100 RPS  average
Peak factor 5×  (lunch + dinner)  ≈ 500 RPS
```

Each order produces several writes:

| Event | Writes |
| --- | --- |
| Order created | 1 row + N item rows |
| Restaurant accepted | 1 status update |
| Order ready | 1 status update |
| Driver assigned | 1 update |
| Delivered | 1 status update |
| **Subtotal: ~5 writes per order** |

```
Order-flow writes/sec at peak:  500 × 5 = 2 500 RPS
```

Comfortable for a properly-tuned Postgres + read replicas. Sharding kicks in at ~3× this.

---

## Driver location stream — the elephant

```
Active drivers: 150 000
Update freq:    1 location every 4 seconds (typical)
Updates/sec:    ~37 500/sec
Updates/day:    ~3.2 B
```

This is **30× the order write volume**. It must NOT go to the orders DB.

**Architecture choice:** stream into Redis (sorted set / geo) for live queries, append to Kafka for archival, downsample to S3 for analytics.

| Storage | Use |
| --- | --- |
| Redis (`GEOADD` keyed per region) | "drivers near point" sub-ms queries |
| Kafka (driver-locations topic) | Replay, downstream consumers |
| S3 (downsampled to 1 min) | Analytics, debugging |

---

## Read QPS

Each session has many reads:

| Read | Per session |
| --- | --- |
| Restaurant list (home / search) | ~10 |
| Menu open | ~3 |
| Cart updates | ~5 |
| Order tracking polling | ~30 (every 10 s for 30 min) |
| **Subtotal:** ~50 reads/order |

```
9 M orders × 50 reads = 450 M reads/day
Peak RPS:  ~25 000 reads/sec
```

Implication:
- Cache restaurants and menus aggressively (Redis + CDN).
- Tracking page should use **WebSocket / Server-Sent Events** rather than 0.1Hz polling.

If we move tracking to push (1 driver location → all subscribed customers via WebSocket), the polling read load drops by ~70%.

---

## Storage

### Orders

```
Per order ≈ 2 KB  (metadata + items + snapshots + audit)
Per day:    9 M × 2 KB ≈ 18 GB
Per 5 yrs:  18 GB × 365 × 5 × 1.5 (growth) ≈ 50 TB
```

Single-node Postgres maxes at ~10 TB before pain. We need **partitioning by month** + cold archive after 6 months.

### Menu data

```
Restaurants × menu items × ~1 KB each
1.5 M × 50 × 1 KB = 75 GB
```

Easily fits in Postgres + Redis cache. Hot 100 K restaurants ≈ 5 GB cached.

### Driver location archive

```
3.2 B updates/day × 50 B (5 floats + driver id) = 160 GB/day
```

Downsample to 1 / minute → 8 GB/day → 15 TB / 5 yr. Use S3 + Glacier.

---

## Bandwidth

### Menu reads

```
Peak menu reads:  ~5 K RPS
Payload:          50 KB compressed
Bandwidth:        250 MB/s = 2 Gbps
```

→ **CDN is mandatory** for menus.

### Tracking push

```
Active orders being tracked at peak: ~50 K
Updates: 1 per 4 s
Payload: 200 B
Bandwidth: 50 K × 0.25 / s × 200 B = 2.5 MB/s = 20 Mbps
```

Trivial. WebSockets handle it easily on a few servers.

---

## Concurrency hot-points

| Hot-point | Why | Solution |
| --- | --- | --- |
| Restaurant accepts new orders | Many concurrent inserts on same restaurant_id | Append-friendly index, no locking |
| Driver pool — atomic assignment | Two dispatches assign same driver | `SELECT ... FOR UPDATE SKIP LOCKED` or Redis CAS |
| Menu item out-of-stock | Multiple orders racing | DB CAS `UPDATE WHERE stock > 0` |
| Surge factor read | High RPS for surge factor | Cache with 5 s TTL |
| Order cancellation vs preparation | Race on status | Optimistic lock with `version` |

---

## Cache sizing

### Hot restaurants

```
Top 100K restaurants × 50 KB menu = 5 GB
Plus images CDN-only.
```

### Hot drivers

```
150K live drivers × 200 B = 30 MB
Plus geo index (~100 MB)
```

A 16 GB Redis cluster is more than enough.

---

## Headroom rules

For each component, plan for **5×** headroom on launch:

| Component | Sized for |
| --- | --- |
| Postgres (orders) | 2 500 writes/sec → provision 12 K/sec |
| Redis | 30 K reads/sec → provision 200 K/sec |
| Kafka | 40 K msg/sec → provision 200 K/sec |
| App tier | 1 000 RPS → provision 5 K with HPA |

---

## Output of this step

```
Order writes:    500 RPS peak (~2.5 K w/sec including status updates)
Order reads:     25 K RPS peak (mostly cached)
Driver loc:      37.5 K writes/sec (NOT in main DB)
Storage:         50 TB / 5 yr (orders) → partition + archive
Bandwidth:       2 Gbps menu reads (CDN), 20 Mbps tracking
Caches:          Redis 16 GB, CDN for menus & images
```

These shape every later choice: storage selection, sharding key, async vs sync, cache placement.
