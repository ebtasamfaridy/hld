# 12 · Elevator — Machine Coding Skeleton

```
src/main/java/com/elevator/
├── domain/      Direction, CarStatus, DoorState, Capacity, HallCall, BuildingMode
├── scheduler/   Car (LOOK), MotorAdapter, DoorAdapter, StubMotor, StubDoor
├── strategy/    DispatchStrategy, NearestCarStrategy, LookAheadStrategy
├── listener/    BuildingListener, ConsoleLogger
├── Dispatcher.java
├── Building.java
└── Main.java
```

## Demo

1. 10-floor building, 3 cars at floor 1, capacity 8 persons.
2. Tick the building; press a few hall calls.
3. Watch dispatcher pick a car; car runs LOOK; arrives, opens, closes.
4. Toggle FIRE mode → all cars head to ground.
