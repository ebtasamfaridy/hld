# 19 · Circuit Breaker (Hystrix / Resilience4j style)

> "A circuit breaker is a micro-controller around a remote call that fails fast when the dependency is down, gives it a chance to recover, and shields the caller from cascading failure." — every senior engineer ever.

## What you will master

- **3 states** — CLOSED, OPEN, HALF_OPEN — and the rules to transition.
- **Sliding window** for failure rate (count-based vs time-based).
- **Failure rate / slow call rate** thresholds.
- **Trip → wait → probe → decide** state machine.
- **Permitted calls in HALF_OPEN** as a probe to detect recovery.
- **Bulkhead** — semaphore-based concurrency limit, complementary to breaker.
- **Combined patterns**: Retry + CircuitBreaker + Bulkhead + Timeout — order matters.
- **Per-resource registry** of breakers (one breaker per downstream).
- **Metrics & events** — observable state transitions.
- **Concurrency** in the breaker itself: lock-free counters, atomic state transitions.

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

- **Count-based windows** are simple; **time-based** windows reflect "the last N seconds".
- **Failure rate** vs **consecutive failures** — the former is better at scale.
- **HALF_OPEN must be probed sparingly** — too many probes during outage make recovery worse.
- **Order in a chain matters**: Bulkhead → CircuitBreaker → Retry → Timeout.
- **Stateless/per-resource** — every downstream gets its own breaker.
