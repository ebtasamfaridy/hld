# 12 · Car Rental — Machine Coding Skeleton

A self-contained Java skeleton focused on the **place-reservation saga**, **pickup/unlock**, and **return/fare-compute**. In-memory repositories so it runs without any external dependency.

## What it demonstrates

- VehicleModel vs Vehicle (logical product vs physical unit).
- **Atomic time-slot reservation** using a `ConcurrentHashMap<(vehicleId, hourBucket), reservationId>` whose `putIfAbsent` mirrors the SQL `INSERT ... ON CONFLICT DO NOTHING`.
- 6-step `placeReservation` saga with rollback on every failure path.
- `Pickup` flow with GPS fence validation and idempotent IoT unlock.
- `Return` flow with `CompositePricing` (5 components) and 2-phase payment (capture-up-to deposit + MIT for difference).
- Idempotency keys that survive retries.
- Cancellation with tiered refund.

## Run the demo

From `12_machine_coding_skeleton/`:

```bash
find src/main/java -name '*.java' > sources.txt
javac -d build/classes @sources.txt
java -cp build/classes com.carrental.Main
```

The demo:
1. Onboards 2 vehicles (Swift, Creta).
2. Two renters race for the same Swift on overlapping windows — exactly one wins.
3. Successful renter picks up the car (GPS validated, IoT unlocked).
4. Returns 60 km later with low fuel — fare breakdown shown.
5. Idempotent retry of the reservation returns the same row, no double charge.
6. Cancellation with tiered refund.

## Directory layout

```
src/main/java/com/carrental
├── Main.java
├── domain/        Money, GeoPoint, HourBucket, TimeWindow, ids, enums
├── catalog/       VehicleModel, Vehicle, CatalogService
├── inventory/     TimeSlot, ReserveResult, SlotInventoryService
├── reservation/   Reservation, ReservationService (saga)
├── trip/          Trip, TripService, IoTAdapter
├── pricing/       PricingComponent + 5 impls + CompositePricing
├── payment/       PaymentGateway, FakeGateway, Payment, PaymentService
└── store/         in-memory repositories
```

## Design highlights

- `SlotInventoryService.reserve(...)` returns a sealed `ReserveResult` (Reserved / Conflict). No exceptions in the hot path.
- `ReservationService.place(...)` is the saga; failures call explicit compensations.
- `CompositePricing` chains `PricingComponent`s; each returns `Money.zero` if it doesn't apply.
- `FakeGateway` is scriptable (`scriptNextAuthFailure`) for deterministic testing of the decline path.
- All money in `long` minor units inside `Money`.
