# 12 · Rate Limiter

> A core infrastructure problem: **how do you cap requests per (key, window) at scale?** The interview tests algorithm depth (5 algorithms with distinct tradeoffs), distributed semantics, and the Lua-in-Redis idiom for atomic check-and-increment.

## What you will master

- Five canonical algorithms — **Token Bucket**, **Leaky Bucket**, **Fixed Window**, **Sliding Window Log**, **Sliding Window Counter** — and exactly when each is the right pick.
- The **burst vs smoothness** tradeoff (Token Bucket allows bursts; Leaky Bucket smooths).
- The **memory vs accuracy** tradeoff (Sliding Log = perfectly accurate but O(N); Sliding Counter = approximate but O(1)).
- **Distributed rate limiting** via Redis with **Lua scripts** for atomic check-and-update.
- **Multi-tier limits** (per-IP + per-user + per-API-key + global).
- **Failure handling** (fail-open vs fail-closed; degraded mode).
- **Headers** (`X-RateLimit-*`, `Retry-After`).

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

- **Token Bucket** is the production default. Allows controlled bursts; cheap O(1) state per key.
- **Sliding Window Counter** is the right approximation when memory is tight and you need fairer behavior than fixed-window's edge spikes.
- **Distributed correctness** demands atomic check-and-increment. With Redis, that means **a single Lua script per check**, not multi-step `INCR + EXPIRE`.
- **Fail-open** is almost always the right choice for app-level rate limiters; the alternative (fail-closed) makes Redis outage your incident.
