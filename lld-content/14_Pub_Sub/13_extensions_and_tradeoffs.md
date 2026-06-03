# 13 · Pub/Sub — Extensions & Tradeoffs

## Extensions

### 1. Idempotent producer
Track `(producerId, sequence)` per partition at the broker. Reject duplicate sequences. Eliminates duplicates from retries.

### 2. Transactions / exactly-once
Producer can group writes across partitions atomically. Combined with idempotency, gives exactly-once stream processing semantics.

### 3. Compacted topics
Background log cleaner rewrites segments keeping only the latest record per key. Useful for "current state" topics (user profile, current price).

### 4. Tiered storage
Keep recent data on local SSD (hot tier); ship older segments to S3 (cold tier). Massive storage savings; slightly higher latency for old reads.

### 5. Cross-cluster replication
MirrorMaker copies topics from one cluster to another. Used for DR, geo replication.

### 6. Schema registry
Validate message schemas (Avro, Protobuf, JSON Schema) at producer and consumer. Prevents incompatible changes.

### 7. KRaft (replace ZooKeeper)
Built-in Raft for metadata. Simpler operationally; better scaling.

### 8. Cooperative rebalancing
Sticky-incremental: only revoke moved partitions. Reduces consumer downtime during rebalance.

### 9. Dead-letter topics
Consumer pattern: if processing fails N times, publish to a `<topic>.dlq` for human inspection.

### 10. Quotas
Per-client throughput / connection limits to prevent a noisy producer/consumer from starving others.

## Tradeoffs

### Partition count

| Few partitions | Many partitions |
| --- | --- |
| Lower per-broker overhead | More parallelism for consumers |
| Less rebalance cost | More files, more memory |
| Limited consumer scaling | Higher overall throughput |
| **Pick**: 2–3× expected peak consumer count |

### `acks=1` vs `acks=all`

| acks=1 | acks=all |
| --- | --- |
| Lower latency | Higher latency |
| Loss possible if leader crashes pre-replication | Durable to all ISR |
| **Pick**: critical data → `acks=all` |

### Compression

| None | LZ4 / Snappy | Gzip |
| --- | --- | --- |
| 0 CPU | small CPU | high CPU |
| Highest bandwidth | 30–50% savings | 70%+ savings |
| **Pick**: LZ4 default; gzip for cold archives |

### Replication factor

| RF=1 | RF=3 | RF=5 |
| --- | --- | --- |
| No durability on failure | Standard durability | Higher durability + cost |
| **Pick**: RF=3, min.isr=2 for production |

### Consumer commit: sync vs async

| Sync | Async |
| --- | --- |
| Slower, safe | Fast, can lose offsets |
| **Pick**: sync at end of batch; async between |

### Stop-the-world vs cooperative rebalance

Cooperative is strictly better; only downside is slightly more complex protocol.

### Single-broker vs cluster

Skeleton uses single broker for clarity. Production: 3–6 brokers minimum for RF=3.

## Open questions

- How long should retention be? (Business: 7 days typical for events; 1 day for metrics.)
- Should we support compaction on critical topics? (Almost always yes for "current state" topics.)
- Number of partitions per topic? (Start with 6–12; scale up if hot.)
- Cross-AZ replication for fault tolerance? (Yes for production.)

## Output

```
Extensions:    idempotent producer, transactions, compaction, tiered storage,
               cross-cluster, schema registry, KRaft, cooperative rebalance, DLQ, quotas
Tradeoffs:     partition count, acks, compression, RF, sync/async commit
Pre-decided:   acks=all, RF=3, min.isr=2, LZ4, cooperative rebalance
Open Qs:       retention, compaction usage, partition counts, cross-AZ
```
