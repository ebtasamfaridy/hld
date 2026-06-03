# 10 · Elevator System — Design Patterns

## 1. Strategy — `DispatchStrategy`
Plug `NearestCarStrategy`, `LookAheadStrategy`, `DestinationDispatchStrategy`. Tunable without engine change.

## 2. Strategy — `MotorAdapter` / `DoorAdapter`
Real hardware vs simulator vs stub for tests.

## 3. State pattern — Car status & Door state
Sealed interface or enum + guards. We choose enum + guards for door (simple), and for Car we keep it as an enum (transitions are few and explicit). For more complex per-state behavior in V2 (e.g., MaintenanceState with diagnostic loops), we'd promote to subclasses.

## 4. Observer — `BuildingListener`
Audit, dashboards, alerts subscribe. The same fan-out pattern as Vending and Streak.

## 5. Command — `HallCall` / `CarCall` as records
Press events are immutable Commands; the dispatcher decides what to do with them.

## 6. Repository — `AuditLog`
Persistence isolated from domain.

## 7. Factory (lite) — `BuildingBuilder`
Fluent construction with car count, floors, capacities, dispatch strategy.

## 8. Single-thread actor per car
Each car has its own scheduled tick. The Building runs N cars × 10 Hz on a small thread pool.

## 9. Composite (lite) — `BuildingMode`
NORMAL / FIRE / EVAC affects all cars. Use a Strategy-of-strategies: when mode is FIRE, override the dispatcher with a `EmergencyDispatchStrategy` that ignores all hall calls and forces all cars to GROUND.

## What we avoid

| Pattern | Why not |
| --- | --- |
| Visitor over Car status | Enum + guards is fine for 4 states |
| Singleton Building | Multiple buildings in V2 |
| Inheritance for car types (express, freight) | Configuration / capacity flags |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | DispatchStrategy | Tunable car selection |
| Strategy | Motor / Door adapters | Hardware abstraction |
| State (enum) | Car status, Door state | Operation legality |
| Observer | BuildingListener | Decoupled audit / alerts |
| Command (record) | HallCall / CarCall | Immutable press event |
| Repository | AuditLog | DB abstraction |
| Composite (lite) | BuildingMode override | FIRE / EVAC forces special dispatch |

## Output

A clean Strategy + State combination. The cost-function dispatcher and LOOK scheduler are the *algorithmic core*; everything else is plumbing.
