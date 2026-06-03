# 08 · Elevator System — Sequence Diagrams

## 1. Hall call (UP from floor 5)

```mermaid
sequenceDiagram
    autonumber
    participant H as Hall Button (floor 5, UP)
    participant B as Building
    participant D as Dispatcher
    participant S as DispatchStrategy
    participant C1 as Car 1 (floor 1, IDLE)
    participant C2 as Car 2 (floor 8, going DOWN)

    H->>B: hallCall(5, UP)
    B->>D: assign(hallCall, [C1, C2])
    D->>S: cost(C1, hallCall)
    S-->>D: 4
    D->>S: cost(C2, hallCall)
    S-->>D: 12 (going wrong direction, penalty)
    D-->>B: pick C1
    B->>C1: addStop(5)
    Note over C1: stops = {5}, direction = UP, status = MOVING
```

## 2. Car arriving and serving

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Car 1
    participant M as MotorAdapter
    participant Door as DoorAdapter
    participant L as Listeners

    loop tick @ 10Hz
        C1->>C1: nextStop()
        C1->>M: moveOneFloor(UP)
        Note over C1: currentFloor 1→2→3→4→5
        C1->>M: stop()
        C1->>Door: open()
        C1->>L: emit DoorOpened
        Note over C1: passengers board, press destination
        C1->>C1: addStop(11)            # car call
        C1->>Door: close()
        C1->>L: emit DoorClosed
    end
```

## 3. Hall call pressed mid-journey (in same direction)

```mermaid
sequenceDiagram
    autonumber
    participant H as Hall (floor 7, UP)
    participant B as Building
    participant D as Dispatcher
    participant C1 as Car 1 (floor 4, going UP, dest=11)

    H->>B: hallCall(7, UP)
    B->>D: assign
    Note over D: C1 already going UP through 7, cost is small
    D-->>B: pick C1
    B->>C1: addStop(7)
    Note over C1: stops = {7, 11}, will pick up at 7 on the way
```

## 4. Car becomes overloaded

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Car 1
    participant Sensor as LoadSensor
    participant Door as DoorAdapter
    participant L as Listeners

    Sensor-->>C1: load=110% (over capacity)
    C1->>L: emit CarOverloaded
    C1->>Door: keep open (do not close)
    Note over C1: passengers must disembark
    Sensor-->>C1: load=95%
    C1->>Door: close()
```

## 5. Fire mode

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operator
    participant B as Building
    participant C1 as Car 1
    participant C2 as Car 2

    Op->>B: setBuildingMode(FIRE)
    B->>C1: clearStops + addStop(GROUND)
    B->>C2: clearStops + addStop(GROUND)
    Note over B: subsequent hallCall() ignored
```

## 6. Car maintenance

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operator
    participant B as Building
    participant D as Dispatcher
    participant C1 as Car 1

    Op->>B: setCarMaintenance(carId=1, true)
    Note over B: wait until C1 is empty (no current load)
    B->>C1: status = OUT_OF_SERVICE
    Note over D: future hall calls excluded from C1
```

## Output

```
Hall flow:    press → dispatcher cost → pick car → addStop → car ticks → arrive → open door
LOOK:         car traverses stops in current direction; reverse only at last stop
Overload:     keep doors open, alarm
Fire:         all cars to ground; ignore further calls
Maintenance:  drain car, then mark OUT_OF_SERVICE
```
