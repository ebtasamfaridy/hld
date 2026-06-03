# 04 · Library Management System

> Staff-grade end-to-end LLD of a library system (single-branch / multi-branch).

## What you will master

- **Book vs Copy** modeling — the catalog vs the physical inventory.
- **Reservation queue** — multiple members want the same book; FIFO with notifications.
- **Fine strategy** — late fees, lost-book fees, damage fees as Strategies.
- **Borrow concurrency** — concurrent borrow attempts on a single copy.
- **State machines** — Copy and Loan states.
- **Multi-branch search and transfer**.

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

- **Book vs Copy** modeling — `04_domain_model.md`
- **Reservation queue** — `04_domain_model.md`, `08_sequence_diagrams.md`
- **Fine strategy** — `10_design_patterns.md`
- **Borrow concurrency** — `11_concurrency_and_scaling.md`
