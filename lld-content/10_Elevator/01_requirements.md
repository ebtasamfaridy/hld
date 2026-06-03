# 01 · Elevator System — Requirements

## Functional requirements

### Core
- Building with N floors and M elevators (cars).
- Two signal sources:
  - **Hall calls**: from a floor, with a *direction* (UP or DOWN). Pressed by people waiting.
  - **Car calls**: from inside a car, specifying a *destination floor*. Pressed by people inside.
- A **dispatcher** assigns each hall call to one of the M cars.
- Each car visits its assigned hall calls and its car calls in an order that minimizes wait time (LOOK / cost-function).
- A car has:
  - capacity limit (max people / max weight),
  - door open/close logic with timeouts,
  - emergency stop button.
- System has modes:
  - **Normal**
  - **Fire**: all cars return to ground; doors open; ignore further calls.
  - **Maintenance**: a specific car is taken offline; dispatcher re-routes.
  - **VIP / Express**: a car is reserved for a specific origin → destination.

### Out of scope
- Building access control / biometrics.
- Per-floor occupancy sensors (we abstract via "people boarded" events).
- Mechanical control beyond an abstract `MotorAdapter` and `DoorAdapter`.

### Extensions
- Destination dispatch (passenger enters destination at hall — modern systems).
- Energy-saving mode (idle cars park near high-traffic floors).
- Weight sensors with overload alarm.
- Multi-shaft layouts (sky lobbies in tall buildings).
- Predictive ML-based dispatch.

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Avg wait time | < 30 s | UX / SLA |
| Max wait time (P99) | < 60 s | SLA |
| Dispatch latency | < 100 ms | Hall press → car assignment |
| Concurrent hall presses | dozens / sec | rush hour |
| Failure isolation | one stuck car doesn't break others | resilience |
| Real-time constraint | per-car simulation tick at ≥ 10 Hz | smoothness |

## Actors

```
Passenger        - presses hall and car buttons
Dispatcher       - group controller; routes hall calls to cars
Car              - per-elevator state machine
MotorAdapter     - hardware abstraction
DoorAdapter      - hardware abstraction
LoadSensor       - capacity check
Operator         - puts cars in maintenance, triggers fire mode
```

## Edge cases

| Case | Handling |
| --- | --- |
| Two passengers press hall-up on same floor | One hall call (idempotent); both passengers board when car arrives |
| Passenger presses car call for current floor | Open door briefly; no movement |
| Car is full; hall call assigned to it | Either (a) reassign at next available, or (b) skip the call; we choose (a) |
| Direction reversal mid-trip | LOOK forbids; serve all in current direction first |
| All cars are out of service | Reject hall call; alert |
| Passenger enters but doesn't press a floor | Idle in car; doors close after timeout |
| Fire alarm during a journey | Abort current journey; return to ground |
| Maintenance taken on a car carrying people | Wait for car to empty before mode switch |
| Power outage | Cars stop at next safe floor; doors open |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Multi-car LOOK dispatch | ✓ | |
| Capacity limit | ✓ | |
| Fire / maintenance modes | ✓ | |
| Cost-function dispatcher | basic | full ETA model |
| Destination dispatch | | ✓ |
| Energy parking | | ✓ |
| Predictive ML | | ✓ |

## Output

```
Actors:    Passenger, Dispatcher, Car, MotorAdapter, DoorAdapter, LoadSensor, Operator
Core FR:   hall + car calls, LOOK scheduling, cost-function dispatch,
           capacity limits, fire/maintenance modes, emergency stop
NFR:       avg wait <30s, p99 <60s, dispatch <100ms, 10Hz tick
```
