# 07 · Elevator System — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Enums & value objects =====
    class Direction {
      <<enumeration>>
      UP
      DOWN
      IDLE
    }
    class CarStatus {
      <<enumeration>>
      IDLE
      MOVING
      DOOR_OPEN
      OUT_OF_SERVICE
    }
    class DoorState {
      <<enumeration>>
      OPEN
      CLOSED
    }
    class BuildingMode {
      <<enumeration>>
      NORMAL
      FIRE
      MAINTENANCE
    }
    class Capacity {
      <<record>>
      -int maxPersons
      -int maxKg
    }
    class HallCall {
      <<record>>
      -int floor
      -Direction direction
      -Instant pressedAt
      +of(floor, direction) HallCall$
    }

    %% ===== Hardware adapters =====
    class MotorAdapter {
      <<interface>>
      +moveOneFloor(direction)
      +stop()
    }
    class StubMotor {
      -PrintStream out
      +moveOneFloor(direction)
      +stop()
    }
    class DoorAdapter {
      <<interface>>
      +open()
      +close()
      +isOpen() boolean
    }
    class StubDoor {
      -boolean open
      +open()
      +close()
      +isOpen() boolean
    }
    MotorAdapter <|.. StubMotor
    DoorAdapter <|.. StubDoor

    %% ===== Scheduler core =====
    class Car {
      -int id
      -int minFloor
      -int maxFloor
      -Capacity capacity
      -MotorAdapter motor
      -DoorAdapter door
      -int currentFloor
      -Direction direction
      -CarStatus status
      -NavigableSet~Integer~ stops
      -int currentLoad
      +addStop(floor)
      +redirectTo(floor)
      +peekNextStop() Optional~Integer~
      +tick() TickAction
      +doorsClosed()
      +canAccept(hc) boolean
      +addLoad(delta)
      +id() int
      +currentFloor() int
      +direction() Direction
      +status() CarStatus
      +stops() NavigableSet~Integer~
      +load() int
      +capacity() Capacity
      +isOutOfService() boolean
      +setOutOfService(v)
    }
    class TickAction {
      <<enumeration>>
      NONE
      IDLE
      MOVED
      ARRIVED
    }
    Car *-- TickAction
    Car o-- "1" Capacity
    Car o-- "1" MotorAdapter
    Car o-- "1" DoorAdapter

    %% ===== Strategy: Dispatch =====
    class DispatchStrategy {
      <<interface>>
      +cost(car, hallCall) long
    }
    class NearestCarStrategy {
      +cost(car, hallCall) long
    }
    class LookAheadStrategy {
      +cost(car, hallCall) long
    }
    DispatchStrategy <|.. NearestCarStrategy
    DispatchStrategy <|.. LookAheadStrategy

    %% ===== Coordinator =====
    class Dispatcher {
      -DispatchStrategy strategy
      +assign(hallCall, cars) Optional~Car~
    }
    Dispatcher o-- "1" DispatchStrategy

    %% ===== Listener (Observer) =====
    class BuildingListener {
      <<interface>>
      +onHallCallPressed(hc)
      +onHallCallAssigned(hc, carId)
      +onCarArrived(carId, floor)
      +onModeChanged(mode)
    }
    class ConsoleLogger {
      -PrintStream out
      +onHallCallPressed(hc)
      +onHallCallAssigned(hc, carId)
      +onCarArrived(carId, floor)
      +onModeChanged(mode)
    }
    BuildingListener <|.. ConsoleLogger

    %% ===== Top-level orchestrator =====
    class Building {
      -List~Car~ cars
      -Dispatcher dispatcher
      -BuildingListener listener
      -int groundFloor
      -BuildingMode mode
      +hallCall(floor, direction)
      +carCall(carId, destination)
      +tick()
      +setMode(mode)
      +mode() BuildingMode
      +cars() List~Car~
    }
    Building o-- "*" Car
    Building o-- "1" Dispatcher
    Building o-- "1" BuildingListener
```

---

## Class diagram

```mermaid
classDiagram
    class Building {
      -List~Car~ cars
      -Dispatcher dispatcher
      -BuildingMode mode
      -int floors
      +hallCall(floor, dir)
      +carCall(carId, dest)
      +tick()
      +setMode(mode)
    }

    class Dispatcher {
      -DispatchStrategy strategy
      +assign(hallCall, cars) Optional~Car~
    }

    class DispatchStrategy {
      <<interface>>
      +cost(car, hallCall) long
    }
    class NearestCarStrategy
    class LookAheadStrategy
    DispatchStrategy <|.. NearestCarStrategy
    DispatchStrategy <|.. LookAheadStrategy

    class Car {
      -int id
      -int currentFloor
      -Direction direction
      -CarStatus status
      -DoorState doorState
      -SortedSet~Integer~ stops
      -Capacity capacity
      -int currentLoad
      -MotorAdapter motor
      -DoorAdapter door
      +tick()
      +addStop(floor)
      +nextStop() Optional~Integer~
      +canAccept(hallCall) boolean
    }

    class MotorAdapter {
      <<interface>>
      +moveOneFloor(direction)
      +stop()
    }
    class DoorAdapter {
      <<interface>>
      +open()
      +close()
      +isOpen() boolean
    }

    class HallCall { +floor; +direction; +pressedAt }
    class CarCall  { +carId; +destination; +pressedAt }

    Building o-- "1..N" Car
    Building o-- Dispatcher
    Dispatcher o-- DispatchStrategy
    Car o-- MotorAdapter
    Car o-- DoorAdapter
```

## Package layout

```
com.elevator
├── domain/
│   ├── Direction.java
│   ├── CarStatus.java
│   ├── DoorState.java
│   ├── Capacity.java
│   ├── HallCall.java
│   ├── CarCall.java
│   ├── BuildingMode.java
│   └── BuildingSnapshot.java
├── scheduler/
│   ├── Car.java                         # the LOOK scheduler
│   ├── MotorAdapter.java
│   ├── DoorAdapter.java
│   └── adapters: StubMotor, StubDoor
├── strategy/
│   ├── DispatchStrategy.java
│   ├── NearestCarStrategy.java
│   └── LookAheadStrategy.java
├── listener/
│   ├── BuildingListener.java
│   └── ConsoleLogger.java
├── Dispatcher.java
├── Building.java
└── Main.java
```

## Why split Dispatcher and Car

Two distinct algorithms:
- Dispatcher = **selection problem** (one of M cars).
- Car = **scheduling problem** (in what order do I visit my stops?).

Same Strategy interface idiom for both. Separation lets you ship destination-dispatch as a richer dispatcher without touching cars.

## Output

A clean separation: `Building` orchestrates, `Dispatcher` selects, `Car` schedules. Strategy interfaces let dispatch and motion behaviors be tuned independently.
