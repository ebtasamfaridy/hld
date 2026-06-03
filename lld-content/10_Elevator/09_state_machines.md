# 09 · Elevator System — State Machines

## Per-car state machine

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> MOVING : addStop and floor != currentFloor
    IDLE --> DOOR_OPEN : addStop and floor == currentFloor

    MOVING --> MOVING : tick (still traveling)
    MOVING --> DOOR_OPEN : tick reaches a stop floor
    DOOR_OPEN --> MOVING : door closes AND more stops
    DOOR_OPEN --> IDLE   : door closes AND no more stops

    IDLE --> OUT_OF_SERVICE : maintenance
    MOVING --> OUT_OF_SERVICE : motor fault
    DOOR_OPEN --> OUT_OF_SERVICE : door fault
    OUT_OF_SERVICE --> IDLE : repaired
```

## Door substates

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPENING : car arrived at stop
    OPENING --> OPEN   : sensor confirms open
    OPEN --> CLOSING   : after dwell time AND no obstruction
    OPEN --> OPEN      : extend dwell on board/disembark
    CLOSING --> CLOSED : sensor confirms closed
    CLOSING --> OPENING : obstruction detected
```

## Direction changes (LOOK invariant)

```mermaid
stateDiagram-v2
    [*] --> IDLE_DIR
    IDLE_DIR --> UP : nextStop > currentFloor
    IDLE_DIR --> DOWN : nextStop < currentFloor
    UP --> UP : more upward stops
    UP --> DOWN : no upward stops, downward stops exist
    UP --> IDLE_DIR : no stops
    DOWN --> DOWN : more downward stops
    DOWN --> UP : no downward stops, upward stops exist
    DOWN --> IDLE_DIR : no stops
```

LOOK forbids reversing direction in the middle — the car commits to "finish all stops in this direction" before turning around.

## Building mode

```mermaid
stateDiagram-v2
    [*] --> NORMAL
    NORMAL --> FIRE : fire alarm
    NORMAL --> EVAC : earthquake / power-loss
    FIRE --> NORMAL : alarm cleared (operator)
    EVAC --> NORMAL : restored
```

In FIRE / EVAC, hall calls are dropped; cars head to GROUND with doors held open.

## Output

```
Car:        IDLE → MOVING ↔ DOOR_OPEN → IDLE; OUT_OF_SERVICE branch
Door:       CLOSED → OPENING → OPEN → CLOSING → CLOSED (with obstruction handling)
Direction:  LOOK invariant — finish current direction first
Building:   NORMAL ↔ FIRE/EVAC
```
