# 10 · BookMyShow — Design Patterns

## 1. Strategy — `SeatLock`
Plug `RedisSeatLock` (production) or `InMemorySeatLock` (tests). Same interface; different correctness guarantees.

## 2. Strategy — `PricingPolicy`
Plug `BasePlusSurgePricing`, `FlatPricing`, `PromoCodePricing`. Composable: a promo policy can wrap a base policy.

## 3. Repository
Each aggregate has a repo: `ShowRepository`, `HoldRepository`, `BookingRepository`. Postgres impls; in-memory for tests.

## 4. Adapter — `PaymentService`
External gateway behind an interface. Idempotency key passed through.

## 5. Outbox pattern (Kafka)
Booking confirm transaction writes to `bookings` AND `outbox` in the same TX. A separate publisher polls the outbox and pushes to Kafka. Guarantees exactly-once event publication.

## 6. Discriminated union — `EntryResult`-style for Hold/Confirm
Hold returns `Created | Conflict`; Confirm returns `Confirmed | Expired | PaymentFailed | SeatConflict`. Sealed; client pattern-matches.

## 7. State pattern (light) — Hold/Booking/Show via enums + guards
The state graphs are simple and rarely change; enums + transition guards in the service are sufficient. Subclassing each state would over-engineer.

## 8. Cache + version invalidation — Show layout
`layout:show:{id}:v{version}`. On any seat-state change, increment version. Reads always hit the latest version automatically.

## 9. Idempotency — `Idempotency-Key` on hold-create / confirm / cancel
Server stores the response keyed by `(user, key)` for 24 h.

## 10. Observer / Pub-Sub — `EventBus` → Kafka → consumers
Notifications, analytics, refund worker all subscribe to `booking.events`.

## What we avoid

| Pattern | Why not |
| --- | --- |
| `SELECT FOR UPDATE` on entire show | Locks too much; kills throughput at show open |
| Daily cron to expire holds | Redis TTL is automatic |
| Subclass per booking state | Enum + guards is simpler |
| Singleton BookingService | Multi-instance scale-out |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | SeatLock | Pluggable hold mechanism |
| Strategy | PricingPolicy | Pluggable pricing |
| Repository | aggregate repos | DB abstraction |
| Adapter | PaymentService | External gateway |
| Outbox | bookings + outbox in one TX | Atomic event publish |
| Discriminated union | HoldResult/ConfirmResult | Exhaustive client handling |
| Cache versioning | layout:{id}:v{v} | Read-through with fast invalidation |
| Idempotency keys | confirm / cancel | Safe retries |
| Observer | EventBus | Decouple notifications |

## Output

The two strategies (SeatLock + PricingPolicy) plus the Outbox pattern carry the design. Everything else is wiring.
