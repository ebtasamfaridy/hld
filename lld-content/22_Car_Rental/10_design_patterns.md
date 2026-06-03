# 10 · Car Rental — Design Patterns

## Patterns at play

| Pattern | Where | Why |
| --- | --- | --- |
| **Saga (orchestrator)** | `ReservationService.place(...)` | Multi-step, multi-service transaction with compensations |
| **Strategy** | `CancellationPolicy`, `PricingComponent`, `BuyBoxAlloc` (cheapest unit of model), `IoTAdapter` (per-vendor) | Pluggable behaviour |
| **Composite** | `CompositePricing` chains `PricingComponent`s | Final fare is composed |
| **Repository** | All aggregates | Hide persistence |
| **Aggregate root (DDD)** | Reservation, Trip, Vehicle, DamageClaim | Enforce invariants |
| **Value object** | `Money`, `GeoPoint`, `HourBucket`, `TimeWindow` | Immutable, equality-by-value |
| **Outbox** | ReservationDB → Kafka | Reliable event publish in same TXN |
| **State pattern (light)** | Reservation, Trip, Payment, Vehicle, DamageClaim — enum + transition validators | State changes guarded by enum-based rules; behaviour stays in services |
| **Idempotency key** | All money-bearing endpoints | Safe retry |
| **Sealed types / ADT** | `ReserveResult` = `Reserved \| Conflict` | Two-path success without exceptions |
| **Specification** | KYC eligibility check, drop-zone validation | Composable boolean rules |
| **Observer / Pub-Sub** | Domain events → notification, fulfillment, reconciliation | Loose coupling via Kafka |
| **Circuit Breaker / Bulkhead** | Payment gateway, IoT adapter | Fail fast when downstream sick |
| **Two-phase payment** | AUTH at booking, CAPTURE at return, MIT for damage | Decouple intent from money movement |

---

## Saga in detail

`ReservationService.place(...)` is the orchestrator:

| Step | What | Compensation |
| --- | --- | --- |
| 1 | Idempotency lookup | (none — read only) |
| 2 | Validate KYC + vehicle | (none) |
| 3 | `inventory.reserve(...)` — INSERT N timeslots | DELETE timeslots WHERE reservation_id |
| 4 | `payment.authorize(...)` — gateway hold | gateway voidAuth |
| 5 | Persist reservation row + outbox in one TXN | DB ROLLBACK |
| 6 | Return 201 | (none) |

Crucial: **payment authorize happens *outside* the slot-insert TXN**. That avoids holding row locks across an external HTTPS call. If steps 4 or 5 fail after step 3, we explicitly compensate step 3.

---

## Composite pricing

Final fare construction is the textbook Composite:

```java
List<PricingComponent> components = List.of(
    new BaseFareComponent(),
    new PerKmComponent(),
    new LateFeeComponent(),
    new FuelComponent(),
    new CleaningFeeComponent()
);
CompositePricing pricing = new CompositePricing(components);
```

Adding a "weekend surge" or "promo discount" is **a new component**, registered in the list. Existing code is untouched. Each component returns `Money.zero` if it doesn't apply (e.g., FuelComponent when `fuelEnd >= fuelStart`), so the breakdown line for that component is just omitted from the user-facing receipt.

Order matters: discounts come last so they reduce the sum, not just one component. We include a `tag` per component to control display ordering separately from compute order.

---

## Idempotency design

| Operation | Key | Constraint location |
| --- | --- | --- |
| Place reservation | client UUID | `UNIQUE(user_id, idempotency_key)` on reservations |
| Cancel | client UUID | UNIQUE on cancel-events table |
| Authorize | reservation_id + retry counter | gateway dedupes |
| Capture at return | trip_id | gateway dedupes |
| MIT damage charge | claim_id | gateway dedupes; explicit consent at booking |
| Webhook events | gateway eventId | UNIQUE on `processed_events` |

Two layers protect from double-charging the user: our own UNIQUE constraints + the gateway's dedup on idempotency-key. Belt and suspenders.

---

## Outbox in this system

Place reservation writes order, payment, and `ReservationCreated` event into the **same TXN**:

```sql
BEGIN;
INSERT INTO reservations (...);
INSERT INTO payments (...);
INSERT INTO outbox (event_type, payload) VALUES ('ReservationCreated', $...);
COMMIT;
```

A separate publisher (Debezium CDC or polling) reads outbox rows and publishes to Kafka. If the publisher dies after publish but before marking — duplicates are deduped by consumers via `eventId`. If COMMIT fails, neither happens.

---

## Strategy in detail

### `CancellationPolicy`

```java
interface CancellationPolicy {
  Money refundFor(Reservation r, Instant now);
  String tierName(Reservation r, Instant now);
}
```

Implementations:
- `TieredCancellationPolicy` — defaults: 100% > 24 h, 50% 6-24 h, 25% 2-6 h, 0% < 2 h.
- `NoRefundPolicy` — for promo bookings.
- `FullRefundPolicy` — for ops-initiated cancellations.

The active policy is per-promo / per-vehicle config; resolved at booking time and stored on the reservation row so a later policy change doesn't change refund amount.

### `IoTAdapter`

```java
interface IoTAdapter {
  void unlock(UUID vehicleId, String idemToken);
  void lock(UUID vehicleId, String idemToken);
}
```

Implementations: `OEMAdapter1`, `OEMAdapter2`, `MockIoT` (for testing). The right adapter is picked from a registry keyed by vehicle's modem-vendor. Adding a new OEM is a new class, no edits to TripService.

### `BuyBoxAllocator` (vehicle pick within model)

When the user searched for "Hyundai Creta" and we have 5 specific Cretas available in their window, we pick one. Strategy:
- `ClosestUnitAllocator` — minimise renter walk distance.
- `MostFuelAllocator` — minimise immediate refuel hassle.
- `LeastWearAllocator` — equalise odometer growth across the fleet.

Default: `ClosestUnitAllocator`. Configurable per city.

---

## State pattern — why enum + table, not GoF

Same reasoning as e-commerce / order systems (see `00_End_To_End_LLD_Tutorial/05_design_patterns.md`):

- State is **persisted as a string** (column on the row). GoF would require serializing class identity.
- Behaviour for each state lives in **services** (cancellation logic, refund computation, capture logic), not on the entity.
- The "interesting question" for state is *which transitions are allowed* — a `Map<S, Set<S>>` answers it in 5 lines.
- Adding a new state means adding the enum value + entry in the transition table. No new class hierarchy.

Where GoF State **would** be appropriate: if we built an interactive vehicle modem state machine where the same input (`unlock`) means radically different things in `LOCKED`, `UNLOCKING`, `UNLOCKED`, `LOST_KEY` modes. We don't model that — IoT is fire-and-forget — so enum + status field is fine.

---

## Sealed types for the reservation result

```java
public sealed interface ReserveResult permits ReserveResult.Reserved, ReserveResult.Conflict {
    record Reserved(List<TimeSlot> slots) implements ReserveResult {}
    record Conflict(List<HourBucket> blocked) implements ReserveResult {}
}
```

This avoids throwing in the hot path. The caller pattern-matches:

```java
return switch (inventory.reserve(...)) {
    case Reserved r -> proceed(r);
    case Conflict c -> error(409, "OUT_OF_STOCK", c.blocked());
};
```

Throwing for the common-failure case (slot conflict) is a code smell — it pollutes stack traces and hurts hot-path latency. The sealed-type approach is idiomatic modern Java.

---

## Patterns we deliberately avoided

| Pattern | Why not |
| --- | --- |
| **2PC across our DB and gateway** | Gateway is external; saga + outbox + idempotency is the modern answer |
| **Holding DB locks during payment auth** | Auth can take 2 s; never hold locks across user/external waits |
| **Microservices per state machine entity** | Reservation, Trip, Vehicle can live in one process for V1; split if scaling pain demands |
| **Storing raw card numbers** | Vault tokens only; card data never touches our DB |
| **Custom job scheduler for sweepers** | Postgres `LISTEN/NOTIFY` or just cron is sufficient at this scale |

---

## Output

```
Saga + outbox + idempotency = correctness spine
Composite pricing            = composable fare components
Strategy                     = CancellationPolicy, IoTAdapter, Allocator
Sealed ADT                   = ReserveResult success/conflict without exceptions
Two-phase payment            = AUTH → CAPTURE; MIT for delayed damage
Enum + transition table      = state machines without class explosion
Circuit breaker + bulkhead   = isolate gateway and IoT outages
```
