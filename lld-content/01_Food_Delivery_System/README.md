# 01 · Food Delivery System (Swiggy / DoorDash)

> A staff-grade end-to-end LLD of an online food delivery platform.

## What you will master

- Modeling **multi-actor** systems (customer, restaurant, driver, dispatch).
- Designing the **order state machine** with side effects per transition.
- Implementing a **dispatch engine** with pluggable matching strategies.
- Using **geospatial indexing** (geohash / S2 / quadtree) for nearby lookup.
- Handling **inventory locking** and **stock decrement** under contention.
- Building **batching** (multi-order pickup) safely.
- Choosing the right **storage** for orders, menus, driver locations, and tracking.

## Read order

| # | File | What it covers |
| - | --- | --- |
| 1 | [01_requirements.md](./01_requirements.md) | FR / NFR / scope |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) | DAU, RPS, storage |
| 3 | [03_hld.md](./03_hld.md) | High-level architecture |
| 4 | [04_domain_model.md](./04_domain_model.md) | Entities, aggregates, invariants |
| 5 | [05_database_design.md](./05_database_design.md) | Schemas, indexes, locking |
| 6 | [06_api_design.md](./06_api_design.md) | REST APIs, idempotency |
| 7 | [07_class_diagrams.md](./07_class_diagrams.md) | Class diagrams |
| 8 | [08_sequence_diagrams.md](./08_sequence_diagrams.md) | Order placement & dispatch |
| 9 | [09_state_machines.md](./09_state_machines.md) | Order, Driver lifecycles |
| 10 | [10_design_patterns.md](./10_design_patterns.md) | Patterns used |
| 11 | [11_concurrency_and_scaling.md](./11_concurrency_and_scaling.md) | Locks, races, scale |
| 12 | [12_machine_coding_skeleton/](./12_machine_coding_skeleton/) | Java skeleton |
| 13 | [13_extensions_and_tradeoffs.md](./13_extensions_and_tradeoffs.md) | Evolution |
| 14 | [14_interviewer_followups.md](./14_interviewer_followups.md) | Staff grilling Q&A |

## System-specific deep dives

- **Order state machine** — `09_state_machines.md`
- **Dispatch algorithm** — `10_design_patterns.md` + `11_concurrency_and_scaling.md`
- **Geospatial indexing** — `11_concurrency_and_scaling.md`
- **Order batching** — `13_extensions_and_tradeoffs.md`
- **Inventory locking** — `05_database_design.md` + `11_concurrency_and_scaling.md`
