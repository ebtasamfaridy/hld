# 17 · Feature Flag System (LaunchDarkly / Unleash style)

> A feature flag service decouples *deploying code* from *releasing features*. The interesting LLD questions are: how do you evaluate a flag in **<1 ms** under huge load, support **percentage rollouts that are stable per user**, and propagate **changes to all servers in seconds**?

## What you will master

- Flag evaluation as a **function**: `isOn(flagKey, evaluationContext) → boolean | variation`.
- **Targeting rules** — boolean expressions over context fields.
- **Percentage rollouts** with **sticky bucketing** via consistent hashing.
- **Variation flags** — A/B/n testing, not just on/off.
- **Prerequisite flags** and dependency graphs.
- **Multi-environment** (dev/staging/prod) and **multi-tenant**.
- **Push-based propagation** (SSE / websockets) vs **pull** (polling).
- **Local in-process evaluation** for low latency; **streaming updates** keep it fresh.
- **Audit log** for every change (who flipped what, when).
- **Kill switches** vs **feature flags** vs **experiments** — different lifetimes.

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

- **Local evaluation + streaming updates** = sub-ms reads + freshness.
- **Sticky bucketing via hash** keeps the same user on the same side of a 10% rollout for the whole experiment.
- **Pull (polling)** is simple and works everywhere; **push (SSE / WS)** is faster but more infra.
- **Targeting rules** are a small expression language — keep it simple, validate strictly.
- **Audit log** is non-negotiable for compliance; every flag mutation is event-sourced.
