# 09 · Vending Machine

> The canonical **State pattern** problem. The poetry of this design is in the state transitions — every interviewer wants to see the explicit state graph and a clean transition table.

## What you will master

- **State pattern** done right (subclass per state, not enum + ifs).
- Modeling **inventory** as a (slot → product, count) map with atomic decrement.
- A **change-making algorithm** that respects available denominations (greedy works for canonical denominations; DP otherwise).
- **Strategy** for payment (Coin, Card, UPI), with refund/return semantics.
- **Idempotency** for double-tapped buttons and replayed payments.
- A **listener** API for every domain event (insert, dispense, error).

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

- **State pattern** with subclasses cleanly captures the operations valid in each state. An enum-with-ifs ages badly.
- **Change-making** is its own algorithm — greedy by largest coin works only when denominations are canonical (1, 5, 10, 25); otherwise use DP.
- **Atomic dispense + change return** must be transactional. Either both happen or both fail; partial dispense is the worst UX.
- **Maintenance mode** is a state, not a flag — service techs interact with the machine differently from customers.
