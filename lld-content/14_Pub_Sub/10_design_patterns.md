# 10 · Pub/Sub — Design Patterns

## 1. Strategy — `Partitioner`
Pluggable algorithm: hash, round-robin, sticky, custom (geo-aware). Producer is parameterized.

## 2. Strategy — Retention policies
`TimeBased`, `SizeBased`, `Compacted`. Configurable per-topic. Each operates on segment lists.

## 3. Append-only log (architectural pattern)
The fundamental data structure. Forces sequential IO, immutability, simple replication, and natural retention.

## 4. Producer–Consumer
The whole system embodies it: producers and consumers decoupled by the broker.

## 5. Observer (broker → controller via heartbeats)
Each broker emits heartbeats to the controller. Controller observes and triggers rebalance/elections.

## 6. Leader–Follower replication
Per-partition. Single writer simplifies consistency.

## 7. Coordinator pattern
Group coordinator acts as the "single point of truth" for a consumer group's membership and assignment.

## 8. Memento — committed offset
The committed offset is a snapshot of "where I was" so the consumer can resume. Stored externally so the consumer can be replaced without losing position.

## 9. Iterator
Consumer iterates over the log. The position is decoupled from the log itself.

## 10. State machine — replica, group, transaction
All complex protocols modeled as explicit FSMs.

## 11. Quorum-style replication
A message is "committed" when written to all ISR (a configurable quorum). HW advances only when quorum acknowledges.

## 12. Idempotency token (producer sequence)
`(producerId, sequence)` uniquely identifies a write. Broker uses it to dedupe retries.

## 13. Two-phase commit (transactions, V2)
Coordinator-driven 2PC across partitions for transactional writes.

## 14. Outbox pattern (downstream consumer integration)
Apps using Kafka often combine DB writes and Kafka publish via the outbox pattern. (Same pattern used in BookMyShow.)

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| Random IO per record | Kills throughput; sequential append is the design choice |
| Single global log | Doesn't scale horizontally; partitions are the answer |
| Per-message ack to producer | Batching is essential; ack per batch |
| Per-record locks | Per-partition locks (single writer) |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Partitioner | Pluggable partition assignment |
| Strategy | Retention policy | Per-topic delete / compact rules |
| Append-only log | PartitionLog | High-throughput sequential storage |
| Leader–Follower | Per partition | Replication + single-writer simplicity |
| Coordinator | Consumer group | Membership + assignment |
| Memento | Committed offset | Resume after restart |
| Quorum | ISR + HW | Durability without strong consistency cost |
| Idempotency | (producerId, sequence) | Safe retries without duplicates |
| 2PC | Transactions (V2) | Cross-partition atomic commit |
| State machine | Replica/group/txn | Make complex protocols explicit |

## Output

The system is the **append-only log + replication + coordinator + idempotency** pattern stack. Almost every other Pub/Sub design in industry pulls from this same playbook.
