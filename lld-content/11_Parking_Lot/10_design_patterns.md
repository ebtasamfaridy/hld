# 10 · Parking Lot — Design Patterns

## 1. Strategy — `AllocationStrategy`
Plug NearestEntrance / BalancedAcrossFloors / BySection. Each iterates spots in a different order.

## 2. Strategy — `PricingStrategy`
Plug FlatHourly / Tiered / FreeFirstWindow. Composable (FreeFirstWindow wraps another).

## 3. Optimistic Concurrency Control (CAS) — `Spot.tryClaim`
The atomic point. In-memory: `AtomicReference.compareAndSet`. In DB: `UPDATE … WHERE occupied=FALSE`. Without this, two gates can claim the same spot.

## 4. Repository — `SpotRepository`, `TicketRepository`
Persistence isolated.

## 5. Observer — `LotListener`
Audit / dashboard / alerts.

## 6. Builder — `ParkingLotBuilder`
Construct with floors, spot grid, strategies.

## 7. Discriminated union — `EntryResult` (Admitted | LotFull)
Sealed; exhaustive handling at the gate.

## 8. Adapter — `PaymentService`
Wraps the external payment gateway.

## 9. Predicate function — `Compatibility.canPark`
Not a class hierarchy — a single function. Pragmatic call: the matrix is small, concentrated, and likely to evolve as one unit.

## 10. State pattern (lite) — Ticket status
Enum + guards (4 states). Subclassing not justified for this small graph.

## What we avoid

| Pattern | Why not |
| --- | --- |
| Subclasses for VehicleType / SpotType | Many-to-many, predicate-driven |
| Visitor over EntryResult | Sealed switch is cleaner |
| Singleton ParkingLot | Multi-lot V2 |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | AllocationStrategy | Pluggable spot selection |
| Strategy | PricingStrategy | Pluggable fee calc |
| CAS / Optimistic | Spot.tryClaim | Atomic spot allocation |
| Repository | Spot/Ticket/Reservation | Persistence abstraction |
| Observer | LotListener | Decoupled audit/dashboards |
| Builder | LotBuilder | Readable construction |
| Discriminated union | EntryResult | Exhaustive client handling |
| Adapter | PaymentService | External gateway boundary |
| Predicate fn | Compatibility | Many-to-many type matching |
| State (enum) | Ticket | Lifecycle stages |

## Output

The two big patterns are **Strategy** (for the two algorithms — allocation and pricing) and **CAS** (for atomic spot claiming). Everything else is supporting cast.
