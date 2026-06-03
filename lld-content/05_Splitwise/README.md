# 05 · Splitwise

> Staff-grade end-to-end LLD of an expense-splitting platform.

## What you will master

- **Expense split algorithms** — equal, exact, percentage, share, item-wise, adjustment.
- **Debt simplification graph** — minimize the number of cash transfers in a group.
- **Settlement engine** — recording payments between users.
- **Strategy pattern** for split rules.
- **Concurrency** for high-write expense logs.
- **Group invariants** — sum of balances always zero.

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

- **Split algorithms** — `04_domain_model.md`, `10_design_patterns.md`
- **Debt simplification graph** — `11_concurrency_and_scaling.md`
- **Settlement engine** — `08_sequence_diagrams.md`
- **Strategy patterns** — `10_design_patterns.md`
