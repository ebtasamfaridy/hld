# 11 · Parking Lot

> A favorite of senior LLD interviews. The goal is to model **vehicles, spots, floors, gates** cleanly and pluck **two distinct strategies** out of the design: a **spot-allocation strategy** and a **pricing strategy**.

## What you will master

- Vehicle/Spot type matching with **subtype hierarchies** (Bike fits Compact/Large; Truck fits only Large).
- **Strategy** for spot allocation (NearestEntrance, BalancedAcrossFloors, BySection).
- **Strategy** for pricing (FlatHourly, Tiered, FreeFirstHour, ElectricChargingSurcharge).
- Reservation flow with **hold + confirm** semantics for paid pre-booking.
- **Concurrency** for spot allocation (atomic claim under load).
- A clean separation between **domain (free seat picking)**, **gateway (entry/exit)**, and **billing**.

## Read order

| # | File |
| - | --- |
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

- **Allocation is a Strategy, not a static rule.** A mall picks NearestEntrance; an airport picks BalancedAcrossFloors.
- **Pricing is a Strategy too.** Pre-paid hourly, surge weekend, free-first-hour for retail tenants.
- **Concurrency on free spots** is the real challenge: two cars at two gates may try to claim the same spot at the same instant. Atomic claim is non-negotiable.
- **Vehicle ↔ Spot compatibility** is a many-to-many predicate, not a 1-to-1 mapping. Express it as a function.
