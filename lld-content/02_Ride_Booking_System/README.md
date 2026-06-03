# 02 · Ride Booking System (Uber / Ola / Lyft)

> Staff-grade end-to-end LLD of an on-demand ride-hailing platform.

## What you will master

- **Matching engine** — rider ↔ driver with sub-second latency.
- **Surge pricing** — supply/demand-based dynamic factor per geohash.
- **Ride lifecycle** — request → match → pickup → drop → complete.
- **Geospatial indexing** — geohash, S2, quadtree.
- **Driver state machine** — online/idle/en-route/on-trip transitions.
- **Cancellation / no-show / SOS** flows.

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

## System-specific deep dives

- **Matching engine**: `10_design_patterns.md` + `11_concurrency_and_scaling.md`
- **Surge pricing**: `10_design_patterns.md` + `04_domain_model.md`
- **Ride lifecycle**: `09_state_machines.md`
- **Geo search**: `11_concurrency_and_scaling.md`
- **Driver state machine**: `09_state_machines.md`
