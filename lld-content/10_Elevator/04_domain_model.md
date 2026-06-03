# 04 · Elevator System — Domain Model

## Aggregates

```text
Building (root)
├── List<Car>
├── Dispatcher                  # group controller
├── BuildingMode                # NORMAL | FIRE | EVAC
├── int floors
└── AuditLog

Car (root)
├── int id
├── int currentFloor
├── Direction direction         # UP | DOWN | IDLE
├── DoorState                   # OPEN | CLOSED | OPENING | CLOSING
├── CarStatus                   # IDLE | MOVING | DOOR_OPEN | OUT_OF_SERVICE
├── SortedSet<Stop> stops       # where this car must stop
├── Capacity capacity
├── int currentLoad             # # of people boarded
├── MotorAdapter motor
├── DoorAdapter door
└── LoadSensor sensor

Stop (record)
├── int floor
├── StopType                    # HALL_CALL | CAR_CALL
├── Direction? hallDirection    # only for HALL_CALL
└── Instant requestedAt

HallCall (record)
├── int floor
├── Direction direction
└── Instant pressedAt

CarCall (record)
├── int carId
├── int destinationFloor
└── Instant pressedAt

Direction enum: UP, DOWN, IDLE
```

## The Stop set — sorted but direction-aware

The car's `stops` is a `TreeSet<Integer>` (or `SortedSet`) of floors. The **current direction** dictates traversal order:
- UP → ascending floors first (≥ currentFloor), then reverse to descending.
- DOWN → mirror.

We don't store direction inside Stop in the simple form; the direction comes from the car. (For destination-dispatch, we'd extend.)

## LOOK algorithm

```java
public Optional<Integer> nextStop() {
    if (stops.isEmpty()) return Optional.empty();
    if (direction == UP) {
        var next = stops.ceiling(currentFloor);
        if (next != null) return Optional.of(next);
        // No more upward stops; reverse
        direction = DOWN;
        return Optional.ofNullable(stops.last());
    } else if (direction == DOWN) {
        var next = stops.floor(currentFloor);
        if (next != null) return Optional.of(next);
        direction = UP;
        return Optional.ofNullable(stops.first());
    } else {
        // IDLE: pick nearest
        var hi = stops.ceiling(currentFloor);
        var lo = stops.floor(currentFloor);
        if (hi == null) return Optional.ofNullable(lo);
        if (lo == null) return Optional.ofNullable(hi);
        return Optional.of((hi - currentFloor) <= (currentFloor - lo) ? hi : lo);
    }
}
```

This is the heart of LOOK. Adding `BUSY_PENALTY`, `DESTINATION_DISPATCH`, etc., are tweaks to dispatcher cost — not to this algorithm.

## Dispatcher cost function (Strategy)

```java
public interface DispatchStrategy {
    /** Cost of assigning hallCall to car. Smaller is better. */
    long cost(Car car, HallCall hallCall);
}

public final class NearestCarStrategy implements DispatchStrategy {
    public long cost(Car car, HallCall hc) {
        if (!car.canAccept(hc)) return Long.MAX_VALUE;   // capacity / out-of-service
        return Math.abs(car.currentFloor() - hc.floor());
    }
}

public final class LookAheadStrategy implements DispatchStrategy {
    /** Estimated time to pick up = travel time considering current stops + direction. */
    public long cost(Car car, HallCall hc) {
        if (!car.canAccept(hc)) return Long.MAX_VALUE;
        int floors = car.estimatedTravelToFloorWithDirection(hc.floor(), hc.direction());
        long busy = car.stops().size() * 1L;          // stops slow it down
        return floors * 10L + busy * 3L;
    }
}
```

Tuning constants (10, 3) are domain-specific. We keep them in config, not in code constants.

## Car canAccept

```java
boolean canAccept(HallCall hc) {
    if (status == OUT_OF_SERVICE) return false;
    if (currentLoad >= capacity.maxPersons()) return false;
    return true;   // direction & "in-the-way" are evaluated by cost, not as a hard reject
}
```

## Capacity

```java
public record Capacity(int maxPersons, int maxKg) { }
```

Trigger overload alarm if load exceeds; dispatcher avoids overload cars.

## Domain events

```
- HallCallPressed(floor, direction, ts)
- HallCallAssigned(floor, direction, carId, ts)
- HallCallServed(floor, direction, carId, ts)
- CarCallPressed(carId, destination, ts)
- CarArrived(carId, floor, ts)
- DoorOpened(carId, floor, ts)
- DoorClosed(carId, floor, ts)
- CarOverloaded(carId, ts)
- CarOutOfService(carId, reason, ts)
- BuildingModeChanged(old, new, ts)
```

## Output

```
Aggregates:    Building, Car
Value objects: Stop, HallCall, CarCall, Capacity, Direction
Algorithms:    LOOK (per-car), Strategy (dispatcher cost function)
Modes:         NORMAL / FIRE / EVAC at building level
```
