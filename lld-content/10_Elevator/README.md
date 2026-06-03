# 10 · Elevator System

> A multi-elevator dispatch problem. The interview probes whether you understand **scheduling algorithms** (FCFS / LOOK / SCAN), **per-car state** (idle/moving/door-open), and how a **dispatcher** routes external (hall) calls to the best car.

## What you will master

- **Hall calls** vs **car calls** — different signal sources, different routing.
- **LOOK / SCAN** scheduling: pick up requests on the way; reverse only at the last requested floor.
- **Strategy** for car selection (NearestCar, MostIdle, LookAhead/cost-function).
- **State pattern** per elevator car (`Idle`, `Moving`, `DoorOpen`, `OutOfService`).
- **Concurrency** between dispatcher input and per-car simulation tick.
- **Capacity limits** (weight, person count) and load-rejection.
- **Maintenance / fire mode** — system-level state.

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

- **LOOK** beats SCAN (no need to traverse the entire shaft) and beats FCFS (massive throughput improvement). It's the de-facto industry algorithm.
- **Cost-function dispatcher** (estimated time-to-pickup) outperforms nearest-car when traffic is bursty.
- **Express elevators** (skip-stop logic) are a feature flag, not a different elevator type.
- **Group control** (the dispatcher) is logically separate from per-car control.
