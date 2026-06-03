# 11 · Car Rental — Concurrency and Scaling

## The five hot races

### 1. Two renters, same vehicle, overlapping windows

**Scenario.** Aman and Maya both tap Book on the same Swift for windows that overlap. Their requests reach the server at near-identical millis.

**Naive bug.** Read availability, decide it's free, write the reservation row. Two readers see "free" → both write → vehicle double-booked.

**Fix.** The PK `(vehicle_id, hour_bucket)` is the natural mutex. Both transactions try to INSERT the conflicting hour rows. Postgres serialises on the row-level lock; one TXN wins, the other gets a uniqueness violation (or zero affected with `ON CONFLICT DO NOTHING`). The losing app rolls back its slots, doesn't authorize payment, returns 409 OUT_OF_STOCK with the list of blocked buckets.

```sql
INSERT INTO timeslots (vehicle_id, hour_bucket, reservation_id) VALUES (..., ..., $resv1)
ON CONFLICT DO NOTHING;
-- Verify count = expected; if not, rollback.
```

The system is correct **independent of how many concurrent requests** hit the same vehicle.

### 2. Idempotent place-reservation

**Scenario.** User clicks Book on a flaky network; client retries. Two requests with the same `Idempotency-Key` reach the server.

**Bug.** Two reservations created, two payment auths, user double-blocked on their card.

**Fix.**
```sql
INSERT INTO reservations (id, user_id, ..., idempotency_key)
VALUES (...)
ON CONFLICT (user_id, idempotency_key) DO NOTHING
RETURNING id;
```

If `RETURNING` is empty, look up the existing reservation by key and return it. The second request is a no-op at the DB.

Belt-and-suspenders: payment gateway is also given a deterministic idempotency key (`reservation_id`), so even if our app crashed between TXN commit and 201-response, the gateway dedupes.

### 3. Pickup geofence spoofing

**Scenario.** Renter spoofs GPS to "be at the car" while actually elsewhere.

**Bug.** Vehicle unlocked remotely; theft.

**Fix.** Multi-source location: GPS + cell-tower ID + Wi-Fi SSIDs. Velocity sanity check: if the previous known location was 50 km away 10 min ago, current claim is suspect. Plus an explicit "tap unlock now" only at the parked car (not minutes earlier).

This is mostly anti-fraud, not concurrency, but it's the same hot path so worth naming. Beyond V1 we add device-attestation (Apple App Attest, Play Integrity).

### 4. Mid-trip extension racing the next reservation

**Scenario.** User booked Swift 19:00–21:00. Maya already booked the same Swift 21:00–23:00. Aman taps Extend mid-trip at 20:50.

**Bug.** Naive extension would just write the new end-time; collide with Maya's reservation.

**Fix.** Extension is a *new* mini-reservation:

```sql
INSERT INTO timeslots (vehicle_id, hour_bucket, reservation_id)
VALUES ($vehicle_id, h21, $newResvId)
ON CONFLICT DO NOTHING;
```

Conflict on h21 → 409 SLOTS_UNAVAILABLE. The user is told they must return on time. The original reservation's end_at is unchanged.

### 5. Reservation TTL race

**Scenario.** User puts a reservation in HELD at 18:00 with TTL 18:10. Sweeper runs at 18:10 and expires the reservation. User submits payment at 18:10:01.

**Fix.** Use status-guarded UPDATE for the confirm step:

```sql
UPDATE reservations SET status='CONFIRMED', payment_id=...
 WHERE id = $resvId AND status = 'HELD';
```

If zero rows updated, the reservation already expired — release the just-authorised payment, tell user to re-book. Sweeper itself uses the same guard — `WHERE status='HELD' AND expires_at < now()` — so it can't expire a row already CONFIRMED.

---

## Webhook idempotency

Carrier and payment webhooks retry. Always.

```sql
INSERT INTO processed_events(event_id, source) VALUES ($eventId, 'gateway')
ON CONFLICT DO NOTHING
RETURNING event_id;
```

If RETURNING is empty → already processed → 200, no-op. Otherwise process inside the same TXN as the INSERT.

---

## Optimistic vs. pessimistic locking

| Resource | Strategy | Why |
| --- | --- | --- |
| Timeslot | PK conflict (Postgres row lock under the hood) | Atomic; lock-free at app level |
| Reservation | Insert-only after creation; status updates use status-guarded UPDATE | Immutable in spirit |
| Trip | Optimistic version on status updates | Multiple actors (renter, ops, system) |
| Vehicle | Optimistic version on location/fuel updates | High write rate (IoT pings) |
| Payment | Strict allowed-transitions, no concurrent writes possible | Always serialised through one PaymentService instance per payment |

We never `SELECT FOR UPDATE` on a hot row in the reservation path. The PK conflict approach is faster and scales horizontally without lock-row contention.

---

## Scaling knobs

| Layer | Knob | Default | Effect |
| --- | --- | --- | --- |
| Search ES | replicas | 3 | Read fanout |
| Inventory shards | by `vehicle_id` consistent hash | 16 | Spread slot writes |
| Reservation shards | by `user_id` | 32 | Spread place-reservation across users |
| Reservation partitions | by month | 1/mo | Cold storage rotation |
| Cart-like Redis (vehicle locations) | cluster nodes | 16 | Hot key spread |
| Kafka partitions per topic | 32–256 | event throughput |
| Payment gateway connection pool | per-method | 50 | Bulkhead between vendors |
| GPS ingest workers | autoscaled | RPS-driven | Backpressure-friendly |

---

## Failure modes & mitigations

| Failure | Mitigation |
| --- | --- |
| Inventory shard down | Affected vehicles can't be reserved. Catalog soft-marks unavailable. |
| Payment gateway down | Circuit breaker opens; place-reservation returns 503; existing trips' returns queue captures for retry. |
| ReservationDB primary down | Place-reservation fails fast (don't reserve slots we can't persist). 30s auto-failover to replica. |
| Kafka down | Outbox accumulates; place-reservation unaffected (synchronous path doesn't depend on Kafka publish). |
| IoT modem unreachable | Two retries with same idempotency token; then ops escalation; user offered alternative vehicle or cancellation. |
| GPS feed lossy | Acceptable; we trust pickup/return GPS + odometer for billing. |
| Reconciliation worker dies | Idempotent; restart resumes via `published_at IS NULL` cursor. |
| Webhook signature invalid | 401; alert on repeated; possible compromise. |

---

## Hot-path latency budget — place-reservation (target p99 < 600 ms)

| Step | Budget | Comment |
| --- | --- | --- |
| Idempotency lookup | 5 ms | Indexed point read |
| KYC + vehicle validation | 10 ms | Cached |
| Slot insert (60 rows × hourly) | 30 ms | Single multi-row INSERT TXN |
| Payment authorize | 400 ms | External gateway dominates |
| Persist reservation TXN | 30 ms | Single multi-row INSERT |
| Build response | 5 ms | |
| **Total** | **~480 ms** | Headroom for retries |

If we observed gateway p99 climbing toward 1 s, we'd shift to **async confirmation** — return HELD immediately and let a worker complete the auth. Trades simplicity for latency stability.

---

## GPS ingest at scale

5K active trips × 2 pings/min = 10K writes/min sustained, peaks at 30K/min. Strategy:
- Client batches 5–10 pings before sending → reduces request count 10×.
- Server route is `POST /trips/{id}/ping` accepting a list.
- Storage path: append to per-trip Kafka topic partition keyed by trip_id (preserves order).
- Hot tier (Postgres) keeps last 7 days; cold tier (S3 Parquet) holds older for analytics.
- The server only reads GPS for: end-trip fare computation (which uses pickup + return + odo) and SOS/safety queries. Mid-trip GPS is rarely read.

---

## Reservation drift reconciliation

Reservation drift = actual usage window ≠ booked window. We don't try to be perfect in real time — a daily reconciliation job:

1. Lists yesterday's COMPLETED reservations.
2. For each, compares booked vs actual windows.
3. Flags severe drift (e.g., > 4 hr late) for ops review.
4. Recomputes late fees that may have been mis-billed.
5. Issues retroactive charges or refunds via MIT (idempotent on `reservation_id + drift_correction`).

Reconciliation is a safety net, not the primary path — late fees are computed and captured at return time.

---

## Output

```
Hot races:    last-slot, idempotency, geofence, extension, TTL
Locking:      PK conflict on timeslots; never hold DB locks across gateway calls
Scaling:      shard inventory by vehicle, reservations by user, partition reservations by month
Failure:      every component degrades independently; payment + IoT both circuit-broken
Latency:      gateway dominates; everything else is single-digit ms
GPS:          batched, Kafka-buffered, tiered to S3 cold storage
Drift:        daily reconciliation job catches severe cases
```
