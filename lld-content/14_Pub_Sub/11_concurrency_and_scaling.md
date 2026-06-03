# 11 · Pub/Sub — Concurrency & Scaling

## Per-partition single-writer

The leader is the only writer to a partition's log. This is **the** architectural choice that makes the rest of the design tractable:
- No multi-writer conflicts.
- Sequence numbers are simple (just monotonic counter).
- Replication is "ship the bytes the leader appended."
- Concurrency at the broker level is "many partitions in parallel," not "many writers per partition."

## Multi-partition parallelism

A broker hosts hundreds of partitions. Each has its own:
- Append lock (per-partition, fine-grained).
- File handle for the active segment.
- Replication channel.

The broker uses a thread pool sized to CPU cores; each partition's append is serialized but partitions run in parallel.

## ISR maintenance

Followers continuously fetch from leader. Leader tracks each follower's "lag" (offset gap, time gap). If lag > threshold, follower removed from ISR. If catches up, rejoins. ISR changes are persisted via the controller.

## Replication race: producer waits for ISR

```
Producer sends batch.
Leader appends locally.
Leader sends to ISR followers (in parallel).
When all ISR ack, advance HW, ack producer.
```

Backpressure: a slow follower extends the producer's RTT. Producers handle via:
- `acks=1` (don't wait for ISR; trade durability for latency).
- `min.insync.replicas` (require N to ack; rest are best-effort).

## Producer batching

Producers buffer records and send in batches. This is the **single biggest throughput multiplier**. A single 100 KB batch is much cheaper than 100×1 KB calls (network overhead, fsync, replication).

Tradeoff: latency vs throughput. `linger.ms=0` = low latency, low throughput. `linger.ms=10` = slightly higher latency, much higher throughput.

## Consumer rebalance

Triggers:
- New consumer joins.
- Consumer leaves (graceful).
- Consumer crashes (heartbeat timeout).
- Topic partition count changes.
- Subscription pattern changes.

Cost:
- All consumers in the group **stop consuming** during rebalance ("stop the world"). For large groups, this is painful.
- Mitigation: **cooperative rebalancing** (Kafka 2.4+) — consumers keep their assignments and only revoke moved partitions.

## Stop-the-world rebalance vs cooperative

```
Stop-the-world:
  consumer A: revoke {P0,P1,P2,P3}
  consumer B: revoke {P4,P5,P6,P7}
  ----wait----
  consumer A: assign {P0,P1,P2,P3}
  consumer B: assign {P4,P5,P6,P7}
  consumer C: assign nothing yet (waits next round)

Cooperative:
  consumer A: revoke {P3} (move to C)
  consumer B: revoke {P7} (move to C)
  -- A and B keep consuming P0,P1,P2 / P4,P5,P6 --
  consumer C: assign {P3, P7}
```

## Scaling

| Knob | Scale axis |
| --- | --- |
| Add brokers | Cluster throughput, storage |
| Increase partitions | Per-topic parallelism (consumer + producer) |
| Increase consumer count | Up to partition count |
| Increase RF | Durability (cost: 2× write amp per follower) |
| Compression | Network + disk savings, CPU cost |
| Larger batches | Throughput, modest latency tradeoff |

## Hot bottlenecks

| Bottleneck | Mitigation |
| --- | --- |
| Hot partition (skewed key) | Increase partitions, rebalance keys, custom partitioner |
| Slow consumer | Add consumers (up to #partitions); profile processing |
| Network saturation | Compression (LZ4), bigger batches |
| Disk full | Earlier retention, tiered storage |
| Controller bottleneck | KRaft scales better than ZooKeeper |

## Failure modes

| Failure | Behavior |
| --- | --- |
| Producer timeout | Retry idempotent send; eventually fail with exception |
| Leader broker dies | Controller elects new leader; producers reconnect |
| Follower lags | Removed from ISR; alert |
| All ISR die | Block writes (CP) or unclean leader election (lose data) |
| Network partition | Minority side unavailable |
| Disk full | Reject writes; alert; trigger retention |
| Consumer crashes | Group rebalances within session.timeout.ms |

## Output

```
Concurrency: per-partition single writer; many partitions in parallel
Throughput:  batching + sequential IO + compression
Replication: leader-follower with ISR; HW = quorum commit
Rebalance:   cooperative (sticky) to minimize disruption
Scale:       add brokers, partitions, consumers; tune RF and batch size
```
