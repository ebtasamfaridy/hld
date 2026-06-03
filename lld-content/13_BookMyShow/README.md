# 13 · BookMyShow (Event / Movie Ticket Booking)

> The classic distributed inventory + concurrency problem: many users trying to book the same seats simultaneously, with strict no-double-booking guarantees, dynamic pricing, and a strong UX requirement that selected seats are visibly held during checkout.

## What you will master

- Modeling **Movie / Show / Theatre / Screen / Seat** as crisp aggregates.
- The **Hold → Reserve → Pay → Confirm** lifecycle with TTL holds.
- **Concurrency** for seat selection: `INSERT IF NOT EXISTS` + transactional commit.
- **Pricing strategies** (base + dynamic surge by occupancy / category).
- **Payment integration** with idempotency-keyed retries.
- **Distributed booking** via Redis `SETNX` for hold and Postgres for confirm.
- The single hardest problem: **expiring an unconfirmed hold without a daily cron**.

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

- **Hold via Redis SETNX with TTL** is the V1 sweet spot — no cron, expiration is automatic.
- **Confirm via Postgres transactions** is non-negotiable — money + seats must be atomic.
- **Selected seats appear "selected" to the user instantly** but only become reserved on the next API hop. The UI lies a little; the backend is the truth.
- **Pricing strategy** decoupled from the booking flow — surge multipliers apply at quote time.
