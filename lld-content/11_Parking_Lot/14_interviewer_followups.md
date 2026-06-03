# 14 · Parking Lot — Interviewer Follow-ups

## Q1. "Two cars at two gates request spots at the same instant. How do you guarantee no double-allocation?"

Atomic claim on the spot:
- In-memory: `AtomicReference.compareAndSet(null, ticketId)` on `currentTicket`.
- DB: `UPDATE spots SET occupied=TRUE, current_ticket=$1 WHERE id=$2 AND occupied=FALSE RETURNING id`. Only one transaction sees the row before the update; the loser gets 0 rows back.

The strategy retries with the next candidate on a lost claim.

---

## Q2. "Why is `Compatibility` a function and not a class hierarchy?"

The vehicle ↔ spot matrix is small (4 vehicle types × 5 spot types) and likely to evolve as one unit (adding `MOTORCYCLE_LARGE` or `RV` requires updating every relevant case anyway). A single switch concentrates the logic where reviewers can scan it.

If we used `interface CanPark { boolean accepts(SpotType s); }` per vehicle, we'd need to remember to update every implementation. **Where the logic lives matters more than how OO it is.**

---

## Q3. "Different lots want different allocation policies. How do you support that?"

`AllocationStrategy` is a Strategy interface. Provide several:
- `NearestEntranceAllocation` (mall)
- `BalancedAcrossFloorsAllocation` (airport)
- `BySectionAllocation` (factory employees)

The lot is configured with one. Switching is a config change, not a code change.

---

## Q4. "How do you charge?"

`PricingStrategy` interface. `FlatHourlyPricing(rateBySpotType)`, `TieredPricing(...)`, `FreeFirstWindowPricing(15, inner)`. Strategies compose — `FreeFirstWindow` decorates an inner pricing.

Operator can hot-swap the strategy. Existing tickets keep the strategy version they were created with — never retroactively rebill.

---

## Q5. "Driver loses ticket. What now?"

Two options:
1. **Charge max-day flat** — simple, slightly aggressive.
2. **Look up by license plate** if LPR'd at entry — recover the ticket.

Most operators charge max-day on lost ticket. Configure as a policy.

---

## Q6. "Reservation flow."

```
POST /reservations  →  HOLD (with held_spot, expires in 15 min)
POST /reservations/{id}/confirm  →  CONFIRMED
On arrival: gate looks up reservation by plate; converts to active Ticket.
```

The held_spot is **excluded** from regular allocation during the reservation window. DB-level: range-overlap exclusion constraint.

---

## Q7. "What's the Operator's force-close flow?"

For barrier faults / damaged vehicles. `POST /lots/{id}/operations/manual-override { ticket_id, reason }`. Logs operator_id and reason; closes ticket; releases spot. Audit shows `OPERATOR_OVERRIDE` event.

---

## Q8. "How do you detect spot misuse (truck in compact spot)?"

V2 with sensors / cameras. On park event, vehicle classifier infers type; if mismatch with ticket vehicle type, alert. Surcharge applies at exit.

V1 doesn't detect; relies on operator visual checks.

---

## Q9. "Optimistic vs pessimistic locking on tickets?"

Optimistic — `UPDATE … WHERE id=? AND version=?`. The conflict is rare (operator and driver both closing the same ticket simultaneously). Pessimistic locking would block both, hurting throughput.

For spot allocation, the same UPDATE + WHERE pattern is used; it's effectively optimistic.

---

## Q10. "EV cars want EV spots for charging, but should fall back to other spots if EV is full."

Compatibility allows EV cars in COMPACT/LARGE/EV. Preference scoring (`Compatibility.preferenceCost`) makes EV spots cheaper for EV cars in the cost function. Strategy iterates by total cost (preference + distance), picking EV first; falls back if all EV spots are taken.

---

## Q11. "Sensor desync — sensor says free but a car's there. What do you trust?"

**Booking state.** Sensors are advisory only. If the system thinks the spot is free, it can be allocated; the driver finds a car parked there → drive to next spot, gate re-allocates. Audit reports the misuse.

We don't let sensors block allocations because sensors are unreliable enough to halt a lot.

---

## Q12. "Pricing changed mid-park. Which rate?"

The rate at *entry time*. We persist `pricing_strategy_version` on the ticket. Pricing computation uses that stored version, not the current one. This is the same pattern as Hotel Booking's `rate_at_book_time`.

---

## Q13. "Most subtle bug a candidate writes?"

Two:
1. **`Spot.tryClaim` not atomic** — `if (!occupied) occupied = true` race. The `compareAndSet` (or DB `UPDATE WHERE`) is mandatory.
2. **Strategy iterates spots without rechecking after a CAS-loss** — they assume the first candidate was the chosen one. Fix: continue iterating on lost CAS.

---

## Q14. "Capacity planning for a 10 K-spot airport lot at peak."

```
Peak entries:           1–2 per second
Peak exits:             1–2 per second
DB writes/sec:          ~5 (entry+exit+payment)
DB reads/sec:           ~50 (dashboards, gate queries, plate lookups)
Index size:             ~10 MB on partial index for free-spot lookup
```

A single Postgres instance handles this trivially.

---

## Q15. "What's the testing strategy?"

- Property-based on allocation: spawn N concurrent threads requesting entry; assert exactly K spots allocated for K compatible vehicles, no double-claim.
- Pricing property-based: random durations + rates; assert nondecreasing fee in time, exact handling of free window edges (14m59s vs 15m1s).
- Edge tests: lot full per type, lost ticket, simultaneous operator override + driver settle.

---

## Output

```
Drill questions covered:
- Atomic spot claim
- Compatibility-as-function
- Allocation/pricing strategies
- Lost ticket / reservation / force-close
- Optimistic locking
- EV preference fallback
- Sensor desync (booking state is truth)
- Pricing version pinning
- Common bugs (non-atomic claim, single-pass iteration)
- Capacity planning
- Testing strategy
```
