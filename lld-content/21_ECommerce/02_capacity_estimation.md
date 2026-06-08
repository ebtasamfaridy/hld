# 02 · E-Commerce — Capacity Estimation

## Scale assumptions (target: top-3 marketplace in a single country)

| Dimension | V1 number | Source |
| --- | --- | --- |
| Registered users | 100,000,000 | |
| MAU | 30,000,000 | 30% of registered |
| DAU | 10,000,000 | 33% of MAU |
| Sellers | 1,000,000 | Active sellers |
| Products (logical) | 50,000,000 | |
| SKUs (variants) | 100,000,000 | ~2 SKUs / product avg |
| Listing offers | 200,000,000 | ~2 offers / SKU avg |
| Orders / day (normal) | 1,000,000 | |
| Orders / day (sale-day peak) | 10,000,000 | 10× spike (Big Billion / Prime Day) |
| Items per order avg | 2.3 | |
| Sellers per order avg | 1.4 | |

---

## Throughput math

- **Search QPS**: ~100 searches per DAU = 1B/day → **12K QPS sustained**, **100K QPS at sale-day peak**.
- **Product detail views**: 5× search = 60K QPS sustained, **500K QPS at peak** (heavily CDN-cached).
- **Cart mutations**: ~5 add/remove per active session × 5M active sessions/day = 25M/day = **300 QPS** sustained, **50K QPS** at sale-launch.
- **Place order**: 1M/day normal = **12 QPS sustained**; 10M/day sale = **5K QPS** at peak (sustained for 1 hour at sale-launch).
- **Inventory writes** (decrement at place-order + increment at cancel/return): ~3× place-order = **15K QPS at peak**.
- **Shipment status updates**: 2.3M shipments/day normal × ~5 status changes each = 12M/day = **140 QPS sustained**.
- **Payment captures**: 1 per shipment dispatch = **30 QPS sustained**, **3K QPS at peak**.
- **Buybox recomputes**: every offer/inventory change = ~50 QPS sustained, ~10K QPS at sale-launch.

| Hot operation | Sustained QPS | Peak QPS | Critical? |
| --- | --- | --- | --- |
| Search | 12K | 100K | Latency-critical, eventual OK |
| Product detail | 60K | 500K | CDN-cached |
| Cart mutate | 300 | 50K | Low-latency, write-heavy |
| Place order | 12 | 5K | **Strongly consistent + low-latency** |
| Inventory decrement | 30 | 15K | **Strongly consistent + atomic** |
| Capture | 30 | 3K | Strongly consistent + idempotent |
| Buybox recompute | 50 | 10K | Eventual ≤ 5 s |

---

## Storage estimates

### Catalog

- 50M products × ~2 KB metadata = **100 GB**.
- 100M SKUs × ~1 KB each = **100 GB**.
- 200M listing offers × ~500 B = **100 GB**.
- Catalog total: **~300 GB**, sharded by product_id; cached aggressively.

### Inventory

- 200M offer rows × ~100 B (seller_id, sku_id, available, reserved, version, updated_at) = **20 GB**. Trivial to fit in Postgres.
- Hot offers (active in last 24 h): ~10M rows; cached in Redis.

### Carts

- 30M MAU × ~10 lines × ~200 B = **60 GB** at any time.
- Carts are session-bound; old carts pruned daily.

### Orders

- 1M/day × 365 × 5 yr = **1.8 B orders** × 2 KB header = **3.6 TB** / 5 yr. Partition by month, shard by user_id.
- OrderItems: 2.3× orders = **4.2 B rows** × 500 B = **2.1 TB**.
- Shipments: 1.4× orders = **2.5 B rows** × 1 KB = **2.5 TB**.

### Payments

- 1M/day × 365 × 5 = **1.8 B authorize records** × 1 KB = **1.8 TB**.
- Captures + refunds × ~2 = additional **3.6 TB**.
- All in Postgres; vault tokens for cards.

### Search index

- ES index over 50M products × ~5 KB indexed text = **250 GB** primary; 3× replication = **750 GB** total.

### Photos + assets

- 50M products × ~6 photos × 200 KB = **60 TB** in S3 + CDN.

### Outbox / events

- ~5 events per order × 1M orders × 1 KB = **5 GB / day**, **150 GB / month** retained 30 days.

---

## Bandwidth

- **Search bandwidth peak**: 100K QPS × ~5 KB = **500 MB/s** out (CDN + Redis serve most).
- **Product detail page**: 500K QPS × ~50 KB JSON = up to **25 GB/s** at peak — CDN absorbs ~95%.
- **Photo CDN**: peaks at hundreds of GB/s during sale events; entirely on the CDN edge.
- **Place-order ingest**: 5K QPS × ~5 KB = **25 MB/s** in.

---

## Hotspots & bottlenecks

| Bottleneck | Reason | Mitigation |
| --- | --- | --- |
| **Last-unit row contention** | Hot SKU on sale day = thousands writing same `inventory_units` row | Optimistic CAS (`WHERE available >= qty`); split hot SKUs into N "buckets" if a single offer attracts > 200 QPS |
| **Buybox recompute storm** | 10M offer changes/day = high write to buybox cache | Debounce per SKU + batch every 1 s; cache result in Redis; recompute on read if stale |
| **Place-order saga latency** | Gateway authorize is 300–800 ms p99 | Saga tolerates the latency; never holds DB locks across the call |
| **Search index lag** | CDC + ES refresh under sale-day write storm | Pre-warmed read replicas; relax ES refresh interval to 5 s during sale; accept brief staleness |
| **Cart hot keys** | Single user cart hammered by client retries | Per-user rate limit on cart mutations |
| **Capture surge during peak fulfilment** | Sellers ship in clusters | Capture is async (queue-driven); not on the buyer hot path |
| **Refund surge after sale** | 7-day return window after Big Billion = spike 7 days later | Refund worker pool autoscales; idempotent retries |

---

## Sharding strategy

| Data | Sharding key | Why |
| --- | --- | --- |
| Products / SKUs | product_id (hash) | Random distribution; reads scale linearly |
| Listing offers | sku_id (hash) | Co-locate offers of same SKU for buybox |
| Inventory units | sku_id (hash) | Co-located with offers |
| Carts | user_id | "My cart" is the dominant read |
| Orders | user_id (hash), partition by month | "My orders" stays single-shard; old orders cold |
| Order items | order_id (co-located with order shard) | |
| Shipments | order_id | |
| Payments | order_id | |
| Sellers / catalog mutations | seller_id | Seller dashboard reads scoped |

---

## Read-vs-write profile

```
Reads >> writes for catalog/search (~1000:1)
Reads ≈ writes for cart (active sessions)
Writes dominate at sale-day place-order (5K QPS sustained for an hour)
Money writes are slow (gateway-bound) but rare relative to reads
```

---

## Latency budgets

| Endpoint | p50 | p99 | Notes |
| --- | --- | --- | --- |
| GET search | 30 ms | 200 ms | ES + Redis facet cache |
| GET product detail | 20 ms | 250 ms | CDN-first, falls back to Postgres |
| POST cart/items | 15 ms | 100 ms | Single Redis write |
| POST orders (place) | 250 ms | 800 ms | Gateway dominant |
| POST shipments/{id}/dispatch | 80 ms | 300 ms | Capture + status update |
| GET orders/{id} | 25 ms | 150 ms | Postgres point read with prefetch |

---

## Output

```
Scale:           100M users, 30M MAU, 50M products, 1M sellers
Hot reads:       search (100K QPS peak), product detail (500K QPS peak via CDN)
Hot writes:      place-order (5K QPS peak), inventory decrement (15K QPS peak)
Storage:         catalog ~300 GB, inventory 20 GB, orders 3.6 TB / 5 yr, photos 60 TB
Sharding:        catalog by product_id, inventory by sku_id, orders by user_id
Bottleneck1:     hot-SKU last-unit contention → optimistic CAS + bucket-split
Bottleneck2:     gateway latency → saga tolerates; capture is async
Bottleneck3:     search index lag during sale → relax refresh interval
```
