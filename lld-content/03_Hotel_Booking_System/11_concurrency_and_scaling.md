# 11 · Hotel Booking — Concurrency & Scaling

## Race conditions

| # | Race | Solution |
| --- | --- | --- |
| 1 | Two bookings for the last room on a date | DB CAS atomic UPDATE |
| 2 | Booking + admin block at same time | DB CAS includes `blocked = FALSE` predicate |
| 3 | Modify + cancel collide | Optimistic lock on Booking via `version` |
| 4 | Payment retry creates duplicate booking | UNIQUE on idempotency_key + UNIQUE on payment idemKey |
| 5 | Hotel deletes room type while booked | Soft-delete with active-booking check |
| 6 | Multi-night booking partial commit | Single transaction + ROLLBACK |
| 7 | Inventory reset by cron while booking | Run cron in low-RPS window; or use partial UNIQUE |
| 8 | Search shows price = X, server quotes Y | HMAC-signed price token with short TTL |

---

## 1. Double-booking prevention — the central problem

This is **the** classic hotel-booking interview question. Master the answer.

### Wrong answer 1: pessimistic lock on hotel

```sql
SELECT * FROM hotels WHERE id=? FOR UPDATE;
```

Locks the entire hotel. Concurrent bookings for different rooms or dates serialize. Awful throughput.

### Wrong answer 2: read availability, then update

```sql
SELECT available_rooms FROM room_inventory WHERE ...;
-- application: if available > 0, decrement
UPDATE room_inventory SET available_rooms = available_rooms - 1 WHERE ...;
```

Race! Two requests both see `available = 1`, both decrement, hotel oversold.

### Right answer: atomic CAS

```sql
UPDATE room_inventory
SET available_rooms = available_rooms - $count, updated_at = now()
WHERE hotel_id = $h AND room_type_id = $r AND date = $d
  AND blocked = FALSE
  AND available_rooms >= $count;
```

If `affected_rows == 1` → reserved. If 0 → no availability. Single round-trip, single SQL statement, no application-side decision-making between read and write.

### Multi-night atomicity

A 5-night booking decrements 5 rows. We need **all-or-nothing**:

```java
@Transactional
public Booking createBooking(CreateBookingCommand cmd) {
  for (LocalDate d : datesIn(cmd.checkIn, cmd.checkOut)) {
    int rows = jdbc.update("UPDATE room_inventory ... WHERE ... AND available_rooms >= ?",
                           cmd.roomCount, ..., cmd.roomCount);
    if (rows == 0) {
      // throw rolls back the @Transactional, undoing prior decrements
      throw new InventoryUnavailableException(d);
    }
  }
  // payment auth, persist booking, outbox
}
```

The `@Transactional` ensures rollback. With READ COMMITTED isolation (Postgres default), each UPDATE serializes on its row independently, so we never see a partial commit from another transaction.

For READ COMMITTED, lost updates between two simultaneous bookings are prevented because each UPDATE re-reads the row at the moment of the update. Even with thousands of concurrent attempts, only `available_rooms` succeed.

### Why not SERIALIZABLE?

We could use SERIALIZABLE isolation. But it adds retries on serialization conflicts (Postgres aborts when its SSI detects a cycle). For our pattern of UPDATE WHERE, READ COMMITTED with the predicate is enough and faster.

---

## 2. Optimistic locking on bookings

Each Booking row has `version`. Modifications:

```sql
UPDATE bookings SET ... , version = version + 1
WHERE id = ? AND version = ?;
```

If 0 rows → conflict; re-read and retry up to 3 times.

This handles concurrent modify+cancel attempts.

---

## 3. Idempotency

Always:

```sql
ALTER TABLE bookings ADD COLUMN idempotency_key VARCHAR(80) UNIQUE;
```

```java
public Booking createBooking(CreateBookingCommand cmd) {
  return bookingRepo.findByIdempotencyKey(cmd.idempotencyKey())
    .orElseGet(() -> persistNew(cmd));
}
```

If two retries race, the UNIQUE constraint ensures one row exists; the loser falls back to read.

Payment authorization uses the same key (with suffix `:auth`). Capture uses `:capture`. Refund uses `:refund`. Each path is at-most-once.

---

## 4. Outbox + idempotent consumers

When booking commits:

```sql
BEGIN;
UPDATE room_inventory ...;
INSERT INTO bookings (...);
INSERT INTO outbox_events (event_type, payload) VALUES ('BookingConfirmed', ...);
COMMIT;
```

A poller reads outbox, publishes to Kafka, marks `published_at`. At-least-once delivery. Consumers (Notification, Search Indexer, Settlement) keep their own dedup table to make handling exactly-once-ish.

---

## 5. Pricing token consistency

We saw it before. The price the user sees is the price they pay. The server signs:

```
HMAC( hotel + room + dates + total + breakdown + exp )
```

Booking POST verifies the token. If expired (~5 min), reject. This avoids:
- Stale prices.
- Tampering.
- Surprise charges.

---

## 6. Modify booking concurrency

```java
@Transactional
public Booking modify(ModifyBookingCommand cmd) {
  Booking b = bookings.findById(cmd.bookingId).orElseThrow();
  // verify b.version() matches expected (optimistic)
  // Compute night delta
  Set<LocalDate> oldNights = b.nights();
  Set<LocalDate> newNights = nightsBetween(cmd.newCheckIn, cmd.newCheckOut);
  Set<LocalDate> toAdd = subtract(newNights, oldNights);
  Set<LocalDate> toRemove = subtract(oldNights, newNights);

  for (var d : toAdd) inventory.decrement(b.hotelId(), b.roomTypeId(), d, cmd.newRoomCount);
  for (var d : toRemove) inventory.increment(b.hotelId(), b.roomTypeId(), d, b.roomCount());

  // re-quote, charge or refund delta
  Money newTotal = pricing.quote(b, cmd).total();
  Money delta = newTotal.subtract(b.totalPrice());
  if (delta.isPositive()) payment.capture(...);
  else if (delta.isNegative()) payment.refund(...);

  b.applyModification(cmd, newTotal);
  return bookings.save(b);
}
```

All within one transaction. If any step fails, roll back.

---

## 7. Block-with-bookings prevention

When admin blocks a date range:

```java
public void blockRange(UUID hotelId, UUID roomTypeId, LocalDate from, LocalDate to) {
  List<Booking> active = bookings.findOverlapping(hotelId, roomTypeId, from, to);
  if (!active.isEmpty() && !force) {
    throw new ActiveBookingsInRangeException(active);
  }
  inventoryRepo.markBlocked(hotelId, roomTypeId, from, to);
  // if force, also cancel and refund affected bookings
}
```

We never silently invalidate a booking.

---

## 8. Scaling

### Vertical → horizontal

- Booking, Inventory, Pricing services run as stateless replicas behind LB.
- Postgres scaled vertically; read replicas for analytics; partitioning per month for inventory.
- Redis for hot caches (search results, availability heatmap).

### Sharding

When write rate per hotel × peak factor exceeds Postgres node capacity:
- Shard `room_inventory` by `hash(hotel_id) % N`.
- Shard `bookings` by `hash(hotel_id) % N` (so all bookings for a hotel are on one shard).
- Cross-hotel queries (admin, analytics) go through ETL.

### Multi-region

Hotels are inherently regional. Each region has its own stack with cross-region read-only replicas for analytics. User accounts are global (shared identity).

### Search scale

- Elasticsearch sized for ~50K RPS at peak.
- CDN for hotel images and static menu data.
- Redis cache layered before ES for top queries.
- ES updates from Postgres CDC (Debezium) — < 30 s lag.

---

## 9. Hotspots and how to handle

### Hot hotel (e.g., a luxury hotel during a wedding season)

Concurrent bookings hammer the same `(hotel_id, room_type_id, date)` row. The atomic UPDATE serializes them on that row, but rows update in microseconds, so 1000 RPS on one row is fine.

Where it becomes a problem:
- Pure UPDATE conflict serialization caps at ~5K UPS per row in Postgres.
- For mega-popular slots: pre-allocate "reservation tokens" — issue tokens in a queue, only token-holders execute decrement. Limits the contention to token-issuance.

### Sale event (1M users at midnight)

All hit search and book the same hotels at once.
- Pre-warm caches.
- Token bucket per user.
- Auto-scale ahead.
- Consider lottery / waitlist at app layer.

---

## 10. Failure modes

| Failure | Mitigation |
| --- | --- |
| DB primary failover | App retries idempotent commands |
| ES out of sync | Search shows slightly stale; booking re-validates |
| Payment gateway down | Circuit breaker; queue retries; degrade to "complete payment later" if business allows |
| Outbox poller down | Events delayed; eventual delivery guaranteed |
| Inventory cron fails | Manual recovery; cron is idempotent (UPSERT) |
| Booking partially confirmed | Reconciliation cron detects and corrects |

---

## Summary

The core technique for hotel booking is the **atomic-decrement calendar inventory model**. Combined with idempotency keys, optimistic locking on Booking, and outbox events, the system has correctness baked in at the SQL level.

Scaling: vertical → read replicas → sharding by hotel.
