# 13 · Elevator System — Extensions & Tradeoffs

## Extensions

### 1. Destination dispatch
Passenger enters destination at the hall (touchscreen / RFID). Dispatcher batches passengers heading to similar floors into the same car. Greatly reduces wait + travel time in tall buildings. Implement as a `DestinationDispatchStrategy` consuming richer hall calls (with destination).

### 2. Express elevators
A car configured to skip floors 1–N (only services 30+). Implement as a per-car `floorRange`. Dispatcher excludes hall calls outside the range.

### 3. Energy parking
Idle cars park near high-traffic floors based on time-of-day stats. Background timer: every 5 min, dispatcher re-positions IDLE cars.

### 4. Predictive ML dispatch
Train a model on (hall press → eventual destination) per-floor, time-of-day. Use predicted destination to pre-position cars. The strategy is the swap point; engine unchanged.

### 5. Weight + person sensors
Replace the simple `currentLoad` int with a `LoadSensor` reading both. Reject overload via door sensor staying open.

### 6. Sky lobby (multi-shaft)
Some buildings have express elevators to floor 50, then locals from there. Two `Building` instances in series, with a shared lobby model.

### 7. Cloud dashboard
Per-building edge controller (V1) → Kafka → cloud audit + dashboards.

### 8. Fault prediction
Door cycles, motor amperage; ML predicts failures. Schedule maintenance proactively.

### 9. ADA / accessibility mode
Extended door dwell, audible announcements, priority boarding (no other car calls accepted while priority pressed).

## Tradeoffs

### LOOK vs SCAN vs FCFS

| Algo | Avg wait | Worst wait | Implementation |
| --- | --- | --- | --- |
| FCFS | bad | good | dead simple |
| SCAN | medium | bounded | unnecessarily traverses to ends |
| LOOK | best | bounded | reverses at last-stop |
| Decision | **LOOK** ✓ industry standard |

### NearestCar vs LookAhead cost function

| Criterion | Nearest | LookAhead |
| --- | --- | --- |
| Code | trivial | tunable constants |
| Behavior in burst | suboptimal | better |
| Implementation | one expression | configurable |
| Decision | **LookAhead default; Nearest for tests** ✓ |

### Threading: per-car thread vs cooperative scheduler

| Criterion | Per-car thread | Cooperative |
| --- | --- | --- |
| Real-time guarantees | yes | not without care |
| Lock complexity | low (lock-free queues) | none |
| Resource per car | a thread | a counter |
| Decision | **Per-car thread** for production; cooperative single-thread for V1 sim. |

### Auto-clear vs manual-clear FIRE mode

Manual. Operator confirms after the alarm is verified false. Auto-clear has caused real-world incidents.

## Open questions

- Exact dwell time: 4s standard, 8s for ADA. Configurable per building.
- Maximum wait before reassignment: if a car has been promised a hall call but isn't moving, when do we reassign? (V1: never reassign once accepted; V2: timer-based.)
- Door obstruction: maximum re-open count before alarm? (3 typical.)

## Output

```
Extensions:    destination dispatch, express, energy parking, ML, sensors, sky-lobby, dashboards
Pre-decided:   LOOK + LookAhead, per-car thread, manual FIRE clear
Open Qs:       reassignment policy, dwell tuning, obstruction handling
```
