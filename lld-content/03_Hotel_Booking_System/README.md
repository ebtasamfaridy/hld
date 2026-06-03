# 03 · Hotel Booking System (Booking.com / OYO / Marriott)

> Staff-grade end-to-end LLD of an online hotel booking platform.

## What you will master

- **Inventory calendar model** — date-by-date room availability.
- **Double-booking prevention** under high contention.
- **Pricing engine** with seasonal, occupancy-based, last-minute rules.
- **State pattern** for booking lifecycle (CREATED → CONFIRMED → CHECKED_IN → CHECKED_OUT).
- **Cancellation policies** as Strategy.
- **Search by date range × city × occupancy**.

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

- **Inventory calendar** — `04_domain_model.md`, `05_database_design.md`
- **Double booking prevention** — `11_concurrency_and_scaling.md`
- **Pricing engine** — `10_design_patterns.md`
- **State pattern for Booking** — `09_state_machines.md`
