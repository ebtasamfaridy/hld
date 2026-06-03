# 14 · Pub/Sub (Kafka-like) Messaging System

> Designing a Kafka-like distributed log. The interview probes whether you understand **partitioned append-only logs**, **consumer groups & offsets**, **replication**, **delivery guarantees**, and the operational realities of brokers vs clients.

## What you will master

- The **append-only partition log** as the fundamental data structure.
- **Producers, brokers, consumers, consumer groups** and how offsets divide work.
- **Partitions** as the unit of parallelism, ordering, and replication.
- **Replication**: leader/follower per partition, ISR, high-watermark.
- **Delivery semantics**: at-least-once, at-most-once, exactly-once (with idempotent producer + transactions).
- **Storage**: segmented files, sparse index, retention policies.
- **Consumer rebalancing**: when a consumer joins/leaves a group.
- The fundamental tradeoff: **ordering within partition vs throughput across partitions**.

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

- **Partitions are the unit of parallelism.** More partitions = more parallelism but more rebalancing cost.
- **Ordering is per-partition only.** A topic isn't ordered globally; that's the price of horizontal scale.
- **Idempotent producer + transactions = exactly-once.** Without both, you're at-least-once at best.
- **Storage is the simple part** (append + segments + retention). **Coordination is the hard part** (controller, ISR, rebalance).
- **Consumer groups** are how multiple consumers share a topic without each seeing every message.
