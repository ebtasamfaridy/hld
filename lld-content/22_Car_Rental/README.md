# 22 · Car Rental (Zoomcar / Drivezy / Revv style)

> A staff-grade end-to-end LLD of a self-drive car-rental platform. The interview probes whether you understand **time-slot inventory**, **multi-unit catalog (model vs vehicle)**, **reservation-to-trip lifecycle**, **multi-component pricing**, and **damage-claim async workflows** at scale.

## What you will master

- Modeling **time-slot inventory** — atomic reservation across an hour-by-hour grid, not a single counter.
- The **VehicleModel vs Vehicle** distinction (logical product vs physical unit), same shape as Library's Book vs Copy.
- **Geo-aware search**: "cars within 3 km of me, available 7 PM tonight to 9 AM tomorrow" — geohash + time predicate.
- The **reservation-to-trip lifecycle**: HELD → CONFIRMED → PICKED_UP → IN_USE → RETURNED, with cancellation, no-show, late-return, and damage arcs.
- **Multi-component pricing computed at return**: base fare + per-km + per-min overage + fuel + late penalty + cleaning + damages.
- **Damage-claim async workflow**: post-trip state machine that may charge the customer days later.
- **Geofenced unlock**: vehicle starts only if rider's GPS is within X metres of the parked car.
- **Reservation drift**: actual trip windows differ from booked windows; final fare reconciles both.
- **Idempotency** at every money-bearing step, including delayed damage charges.
- **Cancellation policy as Strategy**: tiered refunds based on time-to-pickup.

## Read order

| # | File |
| - | --- |
| 0 | [00_simple_language_problem_statement.md](./00_simple_language_problem_statement.md) — **start here** |
| 1 | [01_requirements.md](./01_requirements.md) |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) |
| 3 | [03_hld.md](./03_hld.md) |
| 4 | [04_domain_model.md](./04_domain_model.md) |
| 5 | [05_database_design.md](./05_database_design.md) |
| 6 | [06_api_design.md](./06_api_design.md) |
| 7 | [07_class_diagrams.md](./07_class_diagrams.md) |
| 8 | [08_sequence_diagrams.md](./08_sequence_diagrams.md) |
| 9 | [09_state_machines.md](./09_state_machines.md) |
| 10 | [10_design_patterns.md](./10_design_patterns.md) |
| 11 | [11_concurrency_and_scaling.md](./11_concurrency_and_scaling.md) |
| 12 | [12_machine_coding_skeleton/](./12_machine_coding_skeleton/) |
| 13 | [13_extensions_and_tradeoffs.md](./13_extensions_and_tradeoffs.md) |
| 14 | [14_interviewer_followups.md](./14_interviewer_followups.md) |

## Headline tradeoffs

- **Hourly slots vs continuous time** — the slot grid (1-hour buckets) makes reservation atomic but introduces edge truncation. We chose slots; tradeoff is documented.
- **Reserve-then-pay vs pay-then-reserve** — same as e-commerce; reserve-first protects user from "paid but no car", at the cost of holding inventory during checkout (TTL = 10 min).
- **Pre-authorize vs deposit** — we pre-authorize a buffer at pickup (₹5000) and capture exactly the final fare at return; tradeoff is gateway hold visible to user.
- **Final fare computed at return** — base fare locked at booking, but per-km / overage / damages / fuel only known at return. Pricing engine is component-based.
- **Damage claims as separate aggregate** — ride-end is fast (close trip in seconds), damage assessment is async (hours to days). Avoids blocking returns.

## System-specific deep dives

- **Time-slot atomic reservation** — `05_database_design.md` + `11_concurrency_and_scaling.md`
- **Pickup geofence + condition report** — `08_sequence_diagrams.md`
- **Multi-component pricing at return** — `04_domain_model.md` + `10_design_patterns.md`
- **Damage claim async state machine** — `09_state_machines.md`
- **Reservation drift reconciliation** — `08_sequence_diagrams.md` + `11_concurrency_and_scaling.md`
- **Vehicle vs VehicleModel modelling** — `04_domain_model.md`
