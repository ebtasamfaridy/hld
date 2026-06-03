# 08 · Hotel Booking — Sequence Diagrams

## 1. Search

```mermaid
sequenceDiagram
  participant GA as Guest App
  participant API as Gateway
  participant SS as SearchService
  participant ES as Elasticsearch
  participant R as Redis
  participant INV as InventoryService

  GA->>API: GET /hotels/search
  API->>SS: search(query)
  SS->>R: cache lookup (search:hash)
  alt cache hit
    R-->>SS: top hotels
  else miss
    SS->>ES: query
    ES-->>SS: top hotels
    SS->>R: cache (TTL 30s)
  end
  loop for top-N hotels
    SS->>INV: cheapest available room price for date range
    INV-->>SS: price (or null if no avail)
  end
  SS-->>API: results
  API-->>GA: 200 OK
```

---

## 2. Get hotel + availability

```mermaid
sequenceDiagram
  participant GA
  participant HS as HotelService
  participant INV as InventoryService
  participant PS as PricingService

  GA->>HS: GET /hotels/{id}?dates
  HS->>INV: availability(hotelId, dateRange)
  INV-->>HS: per-night avail
  HS->>PS: quote(hotelId, dateRange, roomCount, occupancy)
  PS-->>HS: priceBreakdown + token (HMAC, exp 5min)
  HS-->>GA: hotel + rooms + price token
```

---

## 3. Create booking — happy path

```mermaid
sequenceDiagram
  autonumber
  participant GA as Guest
  participant BS as BookingService
  participant PS as PricingService
  participant INV as InventoryService
  participant PA as PaymentService
  participant DB as Postgres
  participant K as Kafka

  GA->>BS: POST /bookings (price_token, idemKey)
  BS->>BS: idempotency lookup
  BS->>PS: verify price_token (HMAC + exp)
  BS->>DB: BEGIN
  loop each night in [check_in, check_out)
    BS->>INV: decrement (hotel, roomType, date, count)
    alt failure
      Note over BS: rollback prior decrements
      BS->>DB: ROLLBACK
      BS-->>GA: 409 INVENTORY_UNAVAILABLE
    end
  end
  BS->>PA: authorize(amount, idemKey)
  PA-->>BS: authId
  BS->>DB: insert booking(CONFIRMED) + outbox(BookingConfirmed)
  BS->>DB: COMMIT
  BS-->>GA: 201 {booking}
  Note over K: Kafka publishes via outbox poller
```

The transaction is "all nights or none" via Postgres atomicity.

---

## 4. Booking — partial inventory failure

```mermaid
sequenceDiagram
  participant BS as BookingService
  participant INV as InventoryService

  BS->>INV: decrement night 1   ✓
  BS->>INV: decrement night 2   ✓
  BS->>INV: decrement night 3   ✗ (no avail)
  BS->>INV: increment night 1   (rollback)
  BS->>INV: increment night 2   (rollback)
  Note over BS: ROLLBACK cleans up at DB level too
  BS-->>BS: 409 INVENTORY_UNAVAILABLE {dates: [3]}
```

Postgres ROLLBACK is enough since all three UPDATEs happened in one transaction. We listed the manual increments here for clarity if you do it across services without a single TX.

---

## 5. Cancel booking

```mermaid
sequenceDiagram
  participant GA as Guest
  participant BS as BookingService
  participant INV as InventoryService
  participant POL as CancellationPolicy
  participant PA as PaymentService

  GA->>BS: POST /bookings/{id}:cancel
  BS->>BS: load booking, verify state allows cancel
  BS->>POL: refundFor(booking, now)
  POL-->>BS: Money refund
  BS->>BS: state CONFIRMED -> CANCELLED (CAS on version)
  BS->>INV: increment per-night for booking
  BS->>PA: refund(amount=refund, idemKey)
  PA-->>BS: refund queued
  BS-->>GA: 200 {fee, refund}
```

The cancellation policy snapshot lives in the booking, so applying it is deterministic.

---

## 6. Modify booking (extend by 1 night)

```mermaid
sequenceDiagram
  participant GA as Guest
  participant BS as BookingService
  participant INV as InventoryService
  participant PS as PricingService
  participant PA as PaymentService

  GA->>BS: PATCH /bookings/{id} {check_out: +1d}
  BS->>BS: load booking, allowed if CONFIRMED & not started
  BS->>INV: try decrement night X+1
  alt success
    BS->>PS: re-quote total
    BS->>BS: compute delta = newTotal - oldTotal
    alt delta > 0
      BS->>PA: capture additional or new auth
    else delta < 0
      BS->>PA: refund delta (rare for extensions)
    end
    BS->>BS: update booking + version
    BS-->>GA: 200 {updated}
  else fail
    BS-->>GA: 409 INVENTORY_UNAVAILABLE
  end
```

For shrinking (early checkout), we release inventory and refund per cancellation policy on the released nights.

---

## 7. Block dates with active bookings

```mermaid
sequenceDiagram
  participant HA as Hotel Admin
  participant INV as InventoryService
  participant BS as BookingService

  HA->>INV: POST /hotels/{id}/inventory:block (range, reason)
  INV->>BS: list active bookings overlapping range
  alt any
    INV-->>HA: 409 ACTIVE_BOOKINGS_IN_RANGE {bookingIds}
    Note over HA: Admin can override with force=true and offer alternatives
  else none
    INV->>INV: set blocked=TRUE for each (hotelId, roomTypeId, date)
    INV-->>HA: 204
  end
```

We never silently invalidate confirmed bookings.

---

## 8. Hotel inventory bulk update

```mermaid
sequenceDiagram
  participant HA as Hotel Admin
  participant INV as InventoryService
  participant DB as Postgres
  participant K as Kafka

  HA->>INV: PATCH /hotels/{id}/inventory (ranges)
  INV->>DB: BEGIN
  loop ranges
    INV->>DB: UPSERT room_inventory (hotel, room_type, date, total, price)
  end
  INV->>DB: insert outbox(InventoryUpdated)
  INV->>DB: COMMIT
  Note over K: outbox -> Kafka -> ES via CDC consumer
```

The CDC consumer ensures Search reflects within seconds.

---

## What these reveal

- The booking transaction is the single most important transaction in this system.
- Inventory decrement uses atomic SQL — no locks held for I/O.
- Cancellation policy is deterministic because it's snapshotted.
- Search is eventually consistent; booking re-validates.
- The Outbox pattern keeps events durable and consistent with DB state.
