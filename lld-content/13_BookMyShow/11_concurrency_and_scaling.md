# 11 · BookMyShow — Concurrency & Scaling

## Concurrency hotspots

### 1. Two users, same seat, same instant
**Mitigation**: Redis `SET NX` (single-flight write). The loser sees a `false` return; the orchestrator releases any partially-acquired seats and returns 409.

### 2. Hold expires during payment
**Mitigation**: Atomicity check at confirm:
```sql
UPDATE holds SET status='CONFIRMED'
WHERE id=$1 AND status='HELD' AND expires_at > now()
```
If 0 rows updated → expired. If we already charged, refund.

Better: re-lock the seats on confirm if needed. Or extend hold TTL when payment is initiated (with a hard upper bound).

### 3. Redis split-brain or replication lag
A failover during heavy traffic could let two writers claim the same seat. The Postgres `PRIMARY KEY (show_id, seat_id)` catches this — one of the two confirms fails with `unique_violation`. Refund the loser.

This is **why we don't trust Redis alone**.

### 4. Double-confirm of the same hold
**Mitigation**: Idempotency key on confirm; second call returns the cached response. Also, the SQL `WHERE status='HELD'` guard means the second TX sees `status='CONFIRMED'` and updates 0 rows.

### 5. Pricing change mid-checkout
**Mitigation**: Quote frozen at hold time, persisted in `holds.quote_amount_minor`. Confirm uses the held quote.

### 6. Show going live (open)
**Mitigation**:
- CDN for layout reads.
- `single-flight` at the service: when a layout cache misses, only one request fetches from DB; others wait. Redis-backed via `SETNX layout-loading:{showId}` with short TTL.
- Pre-warm the cache 30 s before the show opens.

### 7. Hot show (Avengers premiere)
- Vertical scale Redis primary for that show's keys (consistent hashing or hash-tagging).
- Throttle holds at API gateway: e.g., 5 holds/min/user.

## Scaling

### Read path
CDN handles 99 % of catalog reads. The hot path for show layout is the Redis cache; cache miss falls back to Postgres + cache populate via single-flight.

### Write path
- Hold: Redis SETNX, ~100 K ops/sec on a single primary.
- Confirm: Postgres TX, ~10 K writes/sec on a single primary.
- For 5 K confirms/sec we need ~1 primary; for 50 K we shard by `show_id`.

### Sharding
- Catalog is small; replicate, don't shard.
- Bookings table partitioned by `created_at`; sharded by `show_id` modulo when needed.
- Redis: hash-tagged by `{show_id}` so all keys for one show land on one shard.

## Hot path latency

| Op | Target | Components |
| --- | --- | --- |
| Layout read (cache hit) | < 50 ms | CDN / Redis |
| Hold | < 200 ms p99 | Redis SETNX + Postgres write |
| Confirm | < 1 s p99 | Payment gateway + Postgres TX |

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Redis down (entire) | Fall back to Postgres-only hold via INSERT with PK guard; degraded latency |
| Postgres write timeout | Retry with idempotency; alert |
| Payment gateway timeout | Idempotency-keyed retry up to N times; refund if eventually fails |
| Kafka down | Outbox accumulates; publisher backs off and retries |
| Network partition between services | Each service degrades independently; reconcile on recovery |

## Outbox pattern (atomic events)

In the same TX as the booking confirm:
```
INSERT bookings (...)
INSERT booking_seats (...)
UPDATE holds (...)
INSERT outbox (topic='booking.events', payload='{...}')
COMMIT
```

A separate worker polls `outbox WHERE published=FALSE`, publishes to Kafka, marks `published=TRUE`. At-least-once delivery; consumers must be idempotent (keyed on `event_id`).

## What we explicitly avoid

- **Daily cron to expire holds.** Redis TTL is the truth.
- **`SELECT FOR UPDATE` on shows.** Locks far too much; would serialize all bookings on a show.
- **Per-seat row-locks for hold.** Postgres can do it but it's slower than Redis SETNX.

## Output

```
Concurrency:    Redis SETNX (hold) + Postgres TX with PK (confirm)
Scaling:        CDN reads; Redis writes; Postgres-shard by show_id at scale
Failure:        Redis fallback to Postgres-only; Outbox for events
Layout cache:   versioned with single-flight loader
```
