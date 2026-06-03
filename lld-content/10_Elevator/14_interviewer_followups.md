# 14 · Elevator System — Interviewer Follow-ups

## Q1. "Hall calls vs car calls — why are they different?"

Hall calls come from outside, with a direction, before anyone has entered any specific car. Car calls come from inside a specific car with a destination floor.

The dispatcher decides **which car** serves a hall call. Once a car has been chosen and a passenger boards, they press their destination — that's a car call. The dispatcher doesn't see car calls; the car owns them.

Conflating them ("just put all destinations in one queue") loses information: hall direction matters for picking the right car (a DOWN-going car shouldn't accept an UP hall call unless it's idle).

---

## Q2. "Walk me through LOOK."

Each car maintains a sorted set of stops. While in `UP` direction, serve the next floor `≥ currentFloor`; if none exist, switch to `DOWN` and serve the highest stop in the set. Mirror for `DOWN`. The car never travels past the highest/lowest pending stop — that's what makes it LOOK and not SCAN.

In code, a `TreeSet<Integer>` with `ceiling(floor)` and `floor(floor)` queries handles both ends.

---

## Q3. "Two cars at floors 5 and 7, both idle. Hall press at floor 6 UP. Who gets it?"

Cost function decides:
- Car at 5: dist=1, going to 6 means traveling UP, matches hall direction.
- Car at 7: dist=1, going to 6 means traveling DOWN — direction conflict with hall UP.

LookAhead penalizes the conflict; pick car at 5.

NearestCar would tie at 1 each → pick first; suboptimal.

---

## Q4. "How do you handle a stuck car?"

Detect via tick timeouts: if `tick()` doesn't advance currentFloor for N consecutive ticks while `MOVING`, treat as a fault. Mark `OUT_OF_SERVICE`; dispatcher reassigns existing hall calls; alert operator.

---

## Q5. "Car becomes full. Subsequent hall press."

Dispatcher excludes that car (`canAccept` returns false). It picks another car or queues the call until a car frees up.

---

## Q6. "Fire alarm during a journey."

`setMode(FIRE)`:
- All cars: clear stops, addStop(GROUND), do not accept new hall calls.
- On arrival at GROUND, doors open and stay open.
- Audit `BUILDING_MODE_CHANGED` and per-car `EMERGENCY_DESCENT`.

When the alarm clears, operator restores `NORMAL`. Cars become idle at GROUND, ready for normal dispatch.

---

## Q7. "Are buttons idempotent?"

Yes. Pressing UP twice from floor 5 enqueues one hall call (set-based). Pressing destination 9 inside a car when stops already contain 9 is a no-op.

This is critical for hardware: bouncing buttons can fire 3-4 times per press.

---

## Q8. "How do you avoid race conditions between dispatcher and car?"

Per-car single-threaded execution. Dispatcher posts `addStop(floor)` to a thread-safe queue (e.g., `ConcurrentLinkedQueue` or `BlockingQueue`). Car drains the queue at the start of each tick.

State the dispatcher reads (`floor`, `direction`, `stop count`) is `volatile` — it might be slightly stale, but cost-function tolerance is fine.

---

## Q9. "Cost function tuning — how do you decide constants?"

Simulate. Generate synthetic traffic patterns (rush hour, off-peak, lunch). Measure avg wait, p95 wait, energy used. Tune `perFloorCost`, `directionConflictPenalty`, `perStopPenalty` until the curves are good for *your* building's traffic.

Avoid hand-coded magic numbers in production — keep them in config.

---

## Q10. "Express elevator?"

A `Car` with a `floorRange`. Dispatcher checks `hallCall.floor` against the range; out-of-range → cost = MAX_VALUE. Hall calls in-range work as normal. Express cars often have higher capacity — so `Capacity(persons=20, kg=1500)`.

---

## Q11. "Destination dispatch?"

Hall calls become `(floor, destinationFloor)` instead of `(floor, direction)`. Dispatcher batches passengers with similar destinations into the same car. Add a `DestinationDispatchStrategy`. The car still uses LOOK internally.

The big win: no need for direction signs at the hall; the system computes optimal grouping.

---

## Q12. "Why per-car thread instead of one event loop?"

Each car needs a 10 Hz tick with bounded latency. A shared loop processes events serially; if one car's tick slows (e.g., logging IO), all cars stall. Per-car threads isolate.

That said, a single-threaded loop is **fine for V1 simulation** — we use it in the skeleton. Production has thread-per-car.

---

## Q13. "What if all cars are full and people keep pressing?"

Hall calls accumulate; once a car has spare capacity (someone disembarks), the dispatcher re-evaluates pending hall calls. The pending queue is bounded (e.g., 100 entries) to avoid runaway memory.

Operator should be alerted: "elevator system saturated."

---

## Q14. "Common candidate mistake?"

Two big ones:
1. **Conflating hall and car calls** into a single queue. Direction info is lost.
2. **Not using LOOK** — implementing FCFS and pretending it's optimal. Watch them simulate two pressures and see if the elevator zigzags.

---

## Q15. "How would you test this?"

- **Property-based**: generate random traffic; assert (a) every hall call eventually served, (b) no overlapping hall calls served by different cars, (c) avg wait ≤ threshold.
- **State-machine tests**: exhaustively press combinations and check car state at each tick.
- **LOOK invariant test**: assert direction never reverses while there's a stop ahead.

---

## Output

```
Drill questions covered:
- Hall vs car calls
- LOOK algorithm
- Cost-function dispatcher tuning
- Stuck car / capacity full / fire mode
- Idempotent buttons
- Concurrency model (per-car thread)
- Express / destination dispatch extensions
- Common bugs (queue conflation, FCFS misuse)
- Testing approach
```
