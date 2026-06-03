# 11 · Ride Booking — Concurrency & Scaling

## Race conditions

| # | Race | Solution |
| --- | --- | --- |
| 1 | Two requests for same driver | `UPDATE WHERE status='IDLE' AND version=?` + `SKIP LOCKED` selection |
| 2 | Driver accepts two offers in parallel | OFFER_PENDING → BUSY transition is CAS |
| 3 | Rider sends duplicate request (network retry) | Idempotency key + UNIQUE constraint |
| 4 | Surge updated mid-quote | Surge factor signed in estimate token; fixed at request time |
| 5 | Match engine vs rider cancel | Optimistic CAS on ride.version; loser reverts |
| 6 | Trip ends + cancel collide | State guards in domain (`requireOneOf(IN_TRIP)`) |
| 7 | Outbox + Kafka double publish | Idempotent consumers (orderId in dedup key) |
| 8 | Driver location stream and trip end | Tracking is async; trip end stops subscription, late events ignored |

---

## Matching engine — deep dive

### Goal

Match a rider's request to the **best available driver** in **< 5 s p95**, **without double-assignment**.

### Step-by-step

```
1. Read pickup geohash (length 7).
2. Redis: GEOSEARCH driver:locations:<city>:<rideType> within 3 km, limit 30.
3. Postgres: SELECT * FROM drivers WHERE id IN (...) AND status='IDLE' (filter by city, type).
4. Score each candidate: ScoringStrategy.score(driver, ride, pickup).
5. Sort descending; take top K (typically 1).
6. Atomic CAS: UPDATE drivers SET status='OFFER_PENDING' ... WHERE status='IDLE'.
7. If 0 rows → next candidate.
8. Insert offer row with expires_at = now + 15s.
9. Push to driver app.
10. Wait for accept (15s).
11. If accept: mark driver EN_ROUTE_PICKUP; mark ride MATCHED; publish event.
12. If decline / expire: revert driver to IDLE; try next.
13. After exhausting candidates (or 90s total): mark ride CANCELLED with reason NO_DRIVERS.
```

### Why CAS at step 6?

Two parallel match runs (for two different rides) could pick the same driver. CAS ensures only one wins.

### Why `SKIP LOCKED` at step 3?

We could replace step 3 with:

```sql
SELECT * FROM drivers
WHERE id IN (...) AND status='IDLE'
ORDER BY ... LIMIT N
FOR UPDATE SKIP LOCKED;
```

This makes selection itself disjoint across concurrent matchers. Useful at very high RPS.

### Latency budget

```
Geo search:            5 ms
DB filter:            10 ms
Scoring (K candidates): 1 ms
CAS:                   5 ms
Push:                 50 ms
Driver decision:    1-15 s
```

p95 match time ≈ p95 driver decision time + 70 ms overhead.

If many declines (low acceptance rate), match latency balloons. Tune by:
- Sending offers in parallel (top-2 simultaneously) — race the first to accept.
- Pre-warming driver pools with feature flags.
- Penalizing decliners.

---

## Geospatial indexing — algorithms

### Geohash mental model

A geohash like `tdr1y3z` represents a rectangle. Same prefix = nearby. Length 7 ≈ 150m × 150m.

To find drivers near point P:
1. Compute geohash of P at length 6 (~600m grid).
2. Look at this cell + 8 neighbors (handles edge cases).
3. Filter results by exact distance.

Redis `GEOSEARCH` does this internally.

### S2 cells

Google's S2 covers the sphere with hierarchical cells using 64-bit integer IDs. Properties:
- Equal-area at all latitudes (geohash is squished near poles).
- Hierarchical (zoom in/out).
- Compact integer keys.

For global services (Uber's scale), S2 is the standard. For single-country services, geohash is fine.

### Quadtree

For dense areas with skewed density, quadtree adapts: subdivide cells where many objects cluster.

In our LLD we use Redis Geo (geohash-based). It's correct, simple, and scales.

### Why not store geo in Postgres?

PostGIS works (~10–30 ms per query). Redis Geo is ~1 ms. Match latency budget says we need Redis.

But Postgres is the **fallback** if Redis is down. We accept slower match during incidents.

---

## Surge pricing — deep dive

### Inputs

```
- pendingRides(zone, type, last 60s)
- idleDrivers(zone, type, current)
- historicalBaseline(zone, type, hour-of-day)
- weather, events (calendar)
```

### Computation (simple)

```
ratio = pending / max(idle, 1)
factor = clamp(1.0 + ratio * 0.5, 1.0, 3.0)

if idle == 0 && pending > 0: factor = 3.0
```

ML version (production): a gradient-boosted model uses 50+ features. The interface is the same:

```java
BigDecimal compute(int idle, int pending, double baseline);
```

### Update cadence

Every 60 s per (zone, type). 1.5K zones × 4 types = 6K calculations/min ≈ 100/sec — trivial.

### Time-decay

Surge cannot persist forever. After 30 min without re-trigger, decay back to 1.0.

### Locking surge for rider

When the rider hits `/rides/estimate`, we sign:

```json
{ "zone": "tdr1y3", "type": "STANDARD", "factor": 1.4, "exp": 1714131000 }
```

with HMAC. Client passes this token to `/rides`. Server verifies HMAC and expiry.

If expired, request a fresh estimate. This prevents:
- Rider "freezing" surge by holding a tab open for hours.
- Inconsistent fare between estimate and book.
- Rider disputing surge they didn't see.

### Why not just re-read at book time?

Two reasons:
1. **UX**: customer expects the price they saw to be honored.
2. **Trust**: re-reading at book may give a different number due to 60 s update windows. That looks adversarial.

---

## Driver state machine concurrency

Critical CAS:

```sql
-- Match engine claims a driver
UPDATE drivers SET status='OFFER_PENDING', current_offer_id=?, version=version+1
WHERE id=? AND status='IDLE' AND version=?;

-- Driver accepts
UPDATE drivers SET status='EN_ROUTE_PICKUP', version=version+1
WHERE id=? AND status='OFFER_PENDING' AND current_offer_id=? AND version=?;

-- Driver finishes trip
UPDATE drivers SET status='IDLE', version=version+1, current_offer_id=NULL
WHERE id=? AND status='IN_TRIP' AND version=?;
```

Each CAS guards both **status** and **identity** (offer_id) where applicable.

---

## Tracking subsystem at scale

100 K concurrent rides being tracked:
- 100 K WebSocket connections.
- 100 K Kafka subscriptions.
- ~25 K pushes/sec.

We use:
- A **fanout layer** (Centrifugo, Pulsar with WebSocket plugin, or AWS API Gateway WS).
- Sticky sessions to a connection node.
- Each connection node consumes Kafka filtered by `ride_id` set.
- Backpressure: if the WS client is slow, drop intermediate updates (keep latest).

Memory: ~5 KB per connection × 100 K = 500 MB per node. With 5 nodes, 100 MB each. Easy.

---

## Scaling plan

### Horizontal

- Stateless services scale on K8s; HPA on RPS.
- DB read replicas for heavy reads (admin, analytics).
- Per-city isolation: each city gets its own match engine instance with its own Redis Geo set.

### Vertical → sharding

When write rate > 10 K/sec on rides, shard by `rider_id`. Most reads are per-rider.

### Multi-region

Each region has full stack. Cross-region only for analytics rollups. Driver pools never cross.

### Cassandra for archive

Driver locations after 7 days of "hot" data move to Cassandra `(driver_id, ts)` partitioning. We read these for trip replay and analytics, never for live matching.

---

## Failure modes

| Failure | Impact | Mitigation |
| --- | --- | --- |
| Redis Geo down | Match latency 10× | Failover to PostGIS |
| Maps API down | Estimate quality drops | Haversine fallback |
| Payment gateway down | New rides blocked | Circuit breaker; degrade gracefully |
| Kafka outage | Side effects delay | Outbox keeps DB consistent |
| Match engine OOM | Backlog | Auto-scale; backlog cron sweeps stuck requests |
| WS fanout node loss | Some riders see freeze | Reconnect logic on client; sticky session re-routes |
| Surge DB stale | Slightly wrong factor | TTL ensures self-healing |
| Driver app GPS spoofing | Phantom drivers | Anomaly detection; ban |

---

## Summary

Every race is closed:
- Idempotency at API layer.
- Optimistic CAS on every state transition.
- Redis Geo + atomic CAS for match.
- HMAC-signed estimates for surge consistency.
- Outbox + idempotent consumers for events.

Scale path: vertical → read replicas → sharding → multi-region.
