# 04 · Parking Lot — Domain Model

## Aggregates

```text
ParkingLot (root)
├── List<Floor>
└── allocation/pricing strategies (config)

Floor
└── List<Spot>

Spot
├── SpotId
├── Floor floor
├── int row, col
├── SpotType (BIKE | COMPACT | LARGE | EV | HANDICAP)
├── boolean occupied
├── TicketId? currentTicket
└── boolean reservedHold        # for V2 reservations

Vehicle (record)
├── String plate
├── VehicleType (BIKE | CAR | TRUCK | EV_CAR)
└── boolean handicapPermit

Ticket (root)
├── TicketId (UUID)
├── String plate
├── VehicleType vehicleType
├── SpotId spot
├── Instant enteredAt
├── Instant? exitedAt
├── Money? feeCharged
├── PaymentRef? payment
└── TicketStatus { ACTIVE, PAID, CLOSED }

Reservation (V2)
├── ReservationId
├── plate
├── start, end
├── SpotId? heldSpot
└── ReservationStatus { HELD, CONFIRMED, CANCELLED, EXPIRED }
```

## Compatibility predicate

A separate function/object — not subclass-encoded:

```java
public final class Compatibility {
    public static boolean canPark(VehicleType v, SpotType s) {
        return switch (v) {
            case BIKE     -> true;                          // BIKE fits anything
            case CAR      -> s != SpotType.BIKE;
            case TRUCK    -> s == SpotType.LARGE;
            case EV_CAR   -> s != SpotType.BIKE;            // any non-BIKE; EV preferred
        };
    }
}
```

Why a function? **Adding a new vehicle/spot type is one update**, not a class hierarchy change.

## AllocationStrategy

```java
public interface AllocationStrategy {
    /** Returns the chosen Spot if any, else empty. Spot is claimed (occupied=true). */
    Optional<Spot> allocate(ParkingLot lot, Vehicle vehicle);
}

public final class NearestEntranceAllocation implements AllocationStrategy {
    public Optional<Spot> allocate(ParkingLot lot, Vehicle v) {
        for (Floor f : lot.floorsSortedByDistance()) {
            for (Spot s : f.spotsSortedByDistanceFromEntrance()) {
                if (!Compatibility.canPark(v.type(), s.type())) continue;
                if (s.tryClaim()) return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}

public final class BalancedAcrossFloorsAllocation implements AllocationStrategy { ... }
public final class BySectionAllocation implements AllocationStrategy { ... }
```

Each strategy iterates spots in a different order; the `tryClaim()` is the **atomic** point.

## PricingStrategy

```java
public interface PricingStrategy {
    Money compute(Ticket ticket, Instant exitTime, SpotType spotType);
}

public final class FlatHourlyPricing implements PricingStrategy {
    private final Map<SpotType, Money> ratePerHour;
    public Money compute(Ticket t, Instant exit, SpotType type) {
        long minutes = Duration.between(t.enteredAt(), exit).toMinutes();
        long hours = (minutes + 59) / 60;        // round up
        return ratePerHour.get(type).times(hours);
    }
}

public final class TieredPricing implements PricingStrategy {
    // First hour = ₹50, then ₹20/hr
}

public final class FreeFirstWindowPricing implements PricingStrategy {
    // Free for first N minutes; then delegate to inner strategy
    private final int freeMinutes;
    private final PricingStrategy inner;
}
```

Strategies compose. `FreeFirstWindowPricing` wraps another → Decorator-ish.

## Ticket

```java
public final class Ticket {
    private final TicketId id;
    private final String plate;
    private final VehicleType vehicleType;
    private final SpotId spot;
    private final Instant enteredAt;
    private Instant exitedAt;
    private Money feeCharged;
    private PaymentRef payment;
    private TicketStatus status = ACTIVE;

    public void close(Instant exit, Money fee, PaymentRef p) {
        this.exitedAt = exit;
        this.feeCharged = fee;
        this.payment = p;
        this.status = CLOSED;
    }
}
```

## Spot — atomic claim

```java
public final class Spot {
    private final AtomicReference<TicketId> currentTicket = new AtomicReference<>();
    // ... other fields

    public boolean tryClaim(TicketId t) {
        return currentTicket.compareAndSet(null, t);
    }
    public void release(TicketId t) {
        currentTicket.compareAndSet(t, null);
    }
    public boolean isOccupied() { return currentTicket.get() != null; }
}
```

In production with a DB, `tryClaim` is an `UPDATE … WHERE occupied=FALSE` returning row count.

## Domain events

```
- VehicleEnteredLot(plate, ticketId, spotId, ts)
- VehicleParkedAtSpot(ticketId, spotId, ts)             # if we model the drive-to-spot
- VehicleExitedLot(ticketId, fee, ts)
- SpotMisuseDetected(ticketId, expectedType, actualType, ts)   # V2 with sensors
- LotFullForType(vehicleType, ts)
```

## Output

```
Aggregates:    ParkingLot, Spot, Ticket
Strategy:      AllocationStrategy, PricingStrategy
Predicate:     Compatibility.canPark (function, not subclass)
Atomic ops:    tryClaim / release with CAS
```
