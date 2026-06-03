# 01 · Pub/Sub — Requirements

## Functional requirements

### Core
- **Topics** — named append-only logs.
- **Partitions** per topic — N immutable, sequenced, durable logs.
- **Producers** can `publish(topic, key, value)`. Same `key` → same partition (preserves per-key ordering).
- **Consumers** can `subscribe(topic, consumerGroup)` and consume from where they left off (committed offset).
- **Consumer groups**: members of a group split partitions; one consumer per partition at a time.
- **Offsets** stored durably (consumer-side or broker-side).
- **Delivery semantics**: at-least-once by default; at-most-once and exactly-once configurable.
- **Replication**: each partition has a leader + followers across N brokers.
- **Retention**: time-based (e.g., 7 days) or size-based (e.g., 10 GB / partition).
- **Compaction** (optional, V2): keep only the latest message per key.
- **Cluster membership**: brokers join/leave; controller elects partition leaders.

### Out of scope
- Built-in transformations / streaming SQL.
- Cross-cluster replication.
- Authentication / TLS / RBAC (mentioned but not designed).

### Extensions
- Schema registry.
- Idempotent producer + transactional producer.
- Tiered storage (hot SSD + cold S3).
- Compacted topics (key-based deduplication).
- Mirror-maker for cross-cluster.

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Throughput per broker | 100 K messages/sec | Standard Kafka spec |
| Throughput per partition | 10 K messages/sec | Sequential disk write |
| Latency (publish→ack) | < 10 ms p99 | Producer SLO |
| Durability (acks=all) | committed = on all ISR | No data loss |
| Availability | 99.99 % | Critical infra |
| Replication factor | 3 | Standard |
| Min ISR | 2 | Quorum-style |

## Actors

```
Producer     - publishes; chooses partition (or via partitioner)
Consumer     - polls; commits offset
ConsumerGroup- group of consumers sharing partitions
Broker       - hosts a subset of partitions
Controller   - one broker; manages cluster metadata
ZooKeeper / KRaft - metadata store (we'll use a simplified controller)
```

## Edge cases

| Case | Handling |
| --- | --- |
| Producer crashes mid-batch | At-least-once: re-send; idempotent producer dedups via sequence number |
| Broker dies (leader of partition) | Controller elects new leader from ISR |
| All ISR replicas die | Either fail (preserve durability) or unclean leader election (lose data) |
| Consumer crashes | Group rebalances; another consumer picks up the partition |
| Slow consumer | Lag accumulates; alert; producer is unaffected |
| Out-of-order publish from one producer | Idempotent producer enforces ordering by sequence number |
| Disk full | Reject publish; alert; trigger retention earlier |
| Network partition | CAP: choose CP (block writes) or AP (allow with risk of split-brain). Kafka chooses CP. |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Topics + partitions + offsets | ✓ | |
| Replication (leader/follower, ISR) | basic | full |
| Consumer groups + rebalance | basic | sticky / cooperative |
| At-least-once | ✓ | |
| Idempotent producer | | ✓ |
| Transactions / exactly-once | | ✓ |
| Compaction | | ✓ |
| Tiered storage | | ✓ |

## Output

```
Actors:    Producer, Consumer (in groups), Broker, Controller
Core FR:   topics, partitions, offsets, consumer groups, replication, retention
NFR:       100K msg/s/broker; 10K/partition; <10ms ack; RF=3, min ISR=2
Edge:      broker death, ISR loss, consumer death, disk full, partition
```
