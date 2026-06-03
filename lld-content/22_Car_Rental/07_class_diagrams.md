# 07 · Car Rental — Class Diagrams

## Package layout

```
com.carrental
 ├── domain/        Money, GeoPoint, HourBucket, TimeWindow, ids, enums
 ├── catalog/       VehicleModel, Vehicle, CatalogService
 ├── inventory/     TimeSlot, SlotInventoryService (atomic reservation)
 ├── reservation/   Reservation, ReservationService (the saga)
 ├── trip/          Trip, TripService
 ├── pricing/       PricingComponent + impls + CompositePricing
 ├── payment/       PaymentGateway, PaymentService, FakeGateway
 └── store/         in-memory repositories
```

---

## Domain & value objects

```mermaid
classDiagram
    class Money {
      +amountMinor: long
      +currency: string
      +add(Money) Money
      +multiply(int) Money
    }
    class GeoPoint {
      +lat: double
      +lng: double
      +distanceTo(GeoPoint) double
    }
    class HourBucket {
      +epochHour: long
      +of(Instant)$ HourBucket
      +next() HourBucket
    }
    class TimeWindow {
      +start: Instant
      +end: Instant
      +hourBuckets() List~HourBucket~
      +overlaps(TimeWindow) boolean
    }
```

---

## Catalog

```mermaid
classDiagram
    class VehicleModel {
      -id: UUID
      -name: string
      -seats: int
      -fuelTankLitres: int
      -hourlyRate: Money
      -perKmRate: Money
    }
    class Vehicle {
      -id: UUID
      -modelId: UUID
      -plate: string
      -cityId: UUID
      -status: VehicleStatus
      -location: GeoPoint
      -lastFuelPct: int
      -lastOdometerKm: int
      +isReservable() boolean
    }
    class CatalogService {
      +addModel(VehicleModel)
      +addVehicle(Vehicle)
      +get(UUID) Vehicle
      +findInCity(UUID) List~Vehicle~
    }
    VehicleModel "1" o-- "*" Vehicle
    CatalogService ..> Vehicle
```

---

## Inventory (the heart of the design)

```mermaid
classDiagram
    class TimeSlot {
      -vehicleId: UUID
      -hourBucket: HourBucket
      -reservationId: UUID
    }
    class SlotInventoryService {
      +reserve(vehicleId, window, reservationId) ReserveResult
      +release(reservationId)
      +isWindowFree(vehicleId, window) boolean
    }
    class ReserveResult {
      <<sealed>>
    }
    class Reserved {
      +slots: List~TimeSlot~
    }
    class Conflict {
      +blocked: List~HourBucket~
    }
    SlotInventoryService ..> ReserveResult
    ReserveResult <|-- Reserved
    ReserveResult <|-- Conflict
```

`SlotInventoryService.reserve(...)` mirrors the atomic SQL INSERT-or-fail. The returned `ReserveResult` is a sealed type — `Reserved` carries the inserted slots; `Conflict` lists the buckets that were already taken so the caller can show an actionable error.

---

## Reservation (the saga)

```mermaid
classDiagram
    class Reservation {
      -id: UUID
      -userId: UUID
      -vehicleId: UUID
      -window: TimeWindow
      -status: ReservationStatus
      -baseFare: Money
      -deposit: Money
      -idempotencyKey: string
      -payment: Payment
      +confirm()
      +cancel(reason)
      +markActive()
      +markCompleted()
    }
    class ReservationService {
      <<saga>>
      +place(userId, vehicleId, window, paymentMethodId, idemKey) Reservation
      +cancel(reservationId, reason)
      +extend(reservationId, newEnd)
      +sweepExpired()
    }
    ReservationService ..> Reservation
    Reservation "1" o-- "1" Payment
```

`ReservationService.place(...)` is the orchestrator:
1. Idempotency lookup.
2. Validate user (KYC) + vehicle (active).
3. Compute base fare + deposit.
4. `SlotInventoryService.reserve(...)` — atomic.
5. `PaymentService.authorize(...)`.
6. Persist Reservation.
7. Return.

Each failure compensates priors (release slots, void auth).

---

## Trip

```mermaid
classDiagram
    class Trip {
      -id: UUID
      -reservationId: UUID
      -pickedUpAt: Instant
      -returnedAt: Instant
      -odoStartKm: int
      -odoEndKm: int
      -fuelStartPct: int
      -fuelEndPct: int
      -finalFare: Money
      -status: TripStatus
      +complete(odoEnd, fuelEnd, fare)
    }
    class TripService {
      +start(reservationId, gps, odoStart, fuelStart) Trip
      +ping(tripId, gps)
      +end(tripId, gps, odoEnd, fuelEnd, photos) Receipt
      +forceLock(tripId)
    }
    class Receipt {
      +finalFare: Money
      +breakdown: List~PricingLine~
      +captureStatus: PaymentStatus
    }
    TripService ..> Trip
    TripService ..> Receipt
```

---

## Pricing (Composite + Strategy)

```mermaid
classDiagram
    class PricingComponent {
      <<interface>>
      +compute(Reservation, Trip) Money
      +name() String
    }
    class BaseFareComponent
    class PerKmComponent
    class LateFeeComponent
    class FuelComponent
    class CleaningFeeComponent
    class CompositePricing {
      -components: List~PricingComponent~
      +breakdown(Reservation, Trip) List~PricingLine~
      +total(Reservation, Trip) Money
    }
    PricingComponent <|.. BaseFareComponent
    PricingComponent <|.. PerKmComponent
    PricingComponent <|.. LateFeeComponent
    PricingComponent <|.. FuelComponent
    PricingComponent <|.. CleaningFeeComponent
    CompositePricing o-- "*" PricingComponent
```

Order matters: the breakdown is presented to the user in the order components are registered. Skip-on-zero is each component's own decision (e.g., FuelComponent returns Money.zero if `fuelEnd >= fuelStart`).

---

## Payment

```mermaid
classDiagram
    class PaymentGateway {
      <<interface>>
      +authorize(reservationId, amount, idemKey) AuthResult
      +capture(authId, amount, idemKey) CaptureResult
      +voidAuth(authId) bool
      +refund(captureId, amount, idemKey) RefundResult
      +mit(savedMethodId, amount, idemKey) ChargeResult
    }
    class FakeGateway
    class PaymentService {
      -gateway: PaymentGateway
      +authorizeDeposit(Reservation) Payment
      +captureUpTo(Payment, Money) Payment
      +chargeDamage(claimId, userId, amount)
    }
    PaymentGateway <|.. FakeGateway
    PaymentService ..> PaymentGateway
```

`mit` (merchant-initiated transaction) is the path for damage charges — uses the saved payment method that the user authorized at booking, with explicit consent recorded.

---

## Why these abstractions

| Abstraction | Why |
| --- | --- |
| `SlotInventoryService` returning sealed `ReserveResult` | Two paths (success / conflict) without throwing exceptions in the hot path |
| `CompositePricing` of `PricingComponent`s | Adding a "promo discount" or "weekend surge" is a new component; doesn't touch existing code |
| `PaymentGateway` interface with `mit` separate | Damage charges require a different auth flow; modelling it as a separate gateway op is cleaner |
| `Reservation` immutable post-CONFIRMED | Edits would corrupt slot accounting; force cancel + rebook |
| `TimeWindow` value object owning `hourBuckets()` | Centralises the "how do we slice into hour buckets" logic |

---

## Output

```
Catalog:    VehicleModel → Vehicle
Inventory:  TimeSlot + SlotInventoryService(reserve, release)
Reservation: aggregate + ReservationService(saga)
Trip:       Trip aggregate + TripService(start, ping, end)
Pricing:    CompositePricing of PricingComponents
Payment:    PaymentGateway interface + PaymentService(authorize, capture, refund, mit)
```
