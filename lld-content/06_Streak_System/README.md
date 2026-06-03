# 06 · Streak System

> Staff-grade end-to-end LLD of a user-engagement streak feature for an existing app (think Spotify / Audible / Duolingo style streaks).

## Problem in one line

> Track how many **consecutive calendar days** each user has performed a configured action (visit the app, or listen to a playlist/episode), expose a calendar view of activity, fire milestones, and let an admin choose which action defines "active."

## What you will master

- **Day-basis** streak math vs rolling-window math (and why it matters).
- Calendar-day computation in the **user's timezone**.
- **Idempotent** activity ingestion (same user pings the server many times per day).
- O(1) streak update on every activity — no expensive recomputation.
- **Strategy pattern** for streak types (`AppVisit`, `Listening`) plus **admin-configurable active type**.
- **Milestone engine** (7-day / 30-day / 100-day) via Observer.
- **Calendar view** API powered by a per-user / per-day activity log.
- Concurrency under multi-device usage.

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

- **Day-basis streak math** → `04_domain_model.md`, `09_state_machines.md`
- **Idempotent activity ingestion** → `11_concurrency_and_scaling.md`
- **Admin-configurable streak type** → `10_design_patterns.md`
- **Calendar view performance** → `05_database_design.md`, `11_concurrency_and_scaling.md`
- **Milestone engine** → `10_design_patterns.md`, `08_sequence_diagrams.md`
