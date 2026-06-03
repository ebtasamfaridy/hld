# 16 · Logger Framework (Log4j / Logback / SLF4J style)

> Designing a logger looks trivial until the interviewer asks: "Now make it async, hierarchical, with multiple appenders, configurable at runtime, with no allocation on the hot path." That's the staff-level question.

## What you will master

- **Hierarchical loggers** — `com.app.module` inherits from `com.app` inherits from root.
- **Log levels** — TRACE/DEBUG/INFO/WARN/ERROR/FATAL — and effective-level resolution.
- **Appenders as Strategy** — Console, File, Rolling File, Syslog, HTTP, Kafka.
- **Layouts / Formatters** — Pattern, JSON, plain — Strategy + Decorator.
- **Filters** — Chain of Responsibility for fine-grained allow/deny.
- **MDC / Diagnostic Context** — `ThreadLocal` map of contextual fields (requestId, userId).
- **Async logging** — ring buffer (LMAX Disruptor), background drain thread, lossy vs lossless.
- **Lazy message construction** — `log.debug("user={}", user)` not `log.debug("user=" + user)`.
- **Rolling file policies** — size-based, time-based.
- **Configuration** — XML/JSON/YAML, runtime reload via watcher.
- **Performance** — minimal allocation, branch-prediction-friendly checks, sampled logging.

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

- **Hierarchy** is the right default for level inheritance — you set DEBUG once on a package, all children inherit.
- **Appenders are pluggable** — same log event can fan out to console + file + Kafka.
- **Async** must be opt-in — wrong defaults will lose logs on crash.
- **Formatting must be lazy** — pre-built strings on disabled levels are the #1 hidden cost.
- **MDC** beats threading context through every method signature.
