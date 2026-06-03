# 14 · Pub/Sub — Interviewer Follow-ups

## Q1. "Why is Kafka so fast?"

Three reasons:
1. **Sequential disk IO** — append-only logs are 100× faster than random IO.
2. **Batching everywhere** — at the producer (group records), broker (batch fsyncs), consumer (fetch many).
3. **Zero-copy** for consumer reads — `sendfile()` from disk → socket without copying through user space.

Plus partition parallelism: many concurrent writers/readers, each on its own log.

---

## Q2. "How does Kafka guarantee ordering?"

**Per partition only.** A topic isn't globally ordered. To ensure ordering for a logical entity (e.g., user X), use that entity's id as the message key — same key always lands on the same partition (via hash partitioner).

Cross-partition order is impossible without serializing all writes through one partition (which kills throughput).

---

## Q3. "What's the difference between at-least-once, at-most-once, exactly-once?"

| Semantic | Producer | Consumer |
| --- | --- | --- |
| At-most-once | `acks=0` or no retry | Commit before processing |
| At-least-once (default) | `acks=all` + retries | Commit after processing |
| Exactly-once | Idempotent producer + transactions | Read-process-write atomically with sendOffsetsToTxn |

Exactly-once requires both ends. Most systems use at-least-once + idempotent consumers (process is idempotent, dedup on application key).

---

## Q4. "Producer sent a message, got no ack, retried. Possible duplicates?"

Without idempotence: yes. Two writes possible if the original ack was just lost.

With idempotence (`enable.idempotence=true`): no. Producer assigns a `(producerId, sequence)`; broker rejects duplicates.

---

## Q5. "Consumer crashes after processing but before commit. What happens?"

It re-reads the same messages (at-least-once). Application must be idempotent (e.g., dedup on a unique business key, or use transactional output).

---

## Q6. "Why is replication leader-follower instead of multi-leader?"

Single-writer makes consistency simple:
- Sequence numbers are trivial (monotonic counter).
- Conflict resolution doesn't exist.
- Replication is "ship the leader's log."

Multi-leader requires conflict resolution (CRDTs, vector clocks). Heavy and slower for streaming workloads.

---

## Q7. "What is ISR and why does it matter?"

ISR = In-Sync Replicas = followers caught up within `replica.lag.time.max.ms`.

A message is "committed" only when all ISR have it. The high-watermark = highest committed offset. Consumers can read only up to HW.

If a follower lags, it's removed from ISR (so it doesn't block the producer). When it catches up, it rejoins.

If `min.insync.replicas` falls below threshold (e.g., 2), the broker rejects writes — preserves durability over availability.

---

## Q8. "Leader dies. What happens to in-flight messages?"

- Messages **acknowledged** to the producer were on all ISR (acks=all). New leader is elected from ISR; data preserved.
- Messages **not yet acked** could be on the leader only. They're lost. Producer should retry (idempotent producer prevents duplicates if it retries an acked one).
- New leader is elected from ISR; if no ISR available, either block writes (CP) or unclean election from a non-ISR replica (loses some data).

---

## Q9. "Walk me through a consumer rebalance."

Trigger: new member joins, member leaves, or member misses heartbeat.

1. Coordinator notifies all current members on next heartbeat: `REBALANCE_IN_PROGRESS`.
2. Members revoke their assignments (in cooperative mode, only the moved partitions; in eager mode, all).
3. All members re-send `JoinGroup`. Coordinator picks one as the **group leader**.
4. Group leader runs the assignment algorithm and sends back via `SyncGroup`.
5. Coordinator distributes assignments to all members.
6. Members start consuming new assignments.

Cost: stop-the-world consumption pause (eager); minimal pause (cooperative).

---

## Q10. "Why store consumer offsets in a special internal topic?"

`__consumer_offsets` is itself a Kafka topic with compaction. Benefits:
- Replicated (durable).
- Compacted (only keep latest per `(group, partition)`).
- Discovered the same way as any other topic.
- Survives controller restarts.

Earlier Kafka used ZooKeeper for offsets — terrible for write-heavy workloads.

---

## Q11. "How do you handle a message you can't process?"

Pattern: dead-letter topic.
1. Try N retries with backoff.
2. If still failing, publish the message + error metadata to `<topic>.dlq`.
3. Operations team inspects DLQ; reprocesses or drops.

This decouples application failure from broker; consumer keeps moving.

---

## Q12. "A topic has hot-key skew (90% of traffic on one key). What do you do?"

- **Add a salt to the key** for high-throughput producer paths: `key_salted = key + ":" + (rand % S)`. Increases partition spread but breaks per-original-key ordering. Acceptable for some use cases.
- **Use a different key** if possible (e.g., `user_id` instead of `country`).
- **Custom partitioner** that distributes the hot key across multiple partitions — deals with ordering carefully.
- **Topic redesign** if the imbalance is structural.

---

## Q13. "How do you prevent a misbehaving producer from overwhelming the broker?"

Quotas:
- `producer_byte_rate` per client.
- `request_rate` per client.

Broker throttles offending producers: delays their responses. They feel backpressure without dropping data.

---

## Q14. "What happens when you increase a topic's partition count from 3 to 5?"

- Existing data stays on partitions 0–2.
- New writes use `hash(key) mod 5`, so a key that previously went to partition 2 may now go to partition 4.
- **Per-key ordering is broken across the partition-count change.** Use this rarely; pre-size partitions if possible.

---

## Q15. "Memory pressure on broker — what's growing?"

- Page cache (good — speeds reads).
- File descriptors (one per segment file).
- Producer state (idempotency map: `(producerId, partition) → last sequence`).
- Replication state (ISR follower offsets).
- Index structures (sparse, but for many partitions sums up).

Mitigation: limit topic count, segment size, retention; expire idle producer state.

---

## Output

```
Drilled:
- Why fast (seq IO, batching, zero-copy)
- Ordering scope (per-partition only)
- At-least vs at-most vs exactly-once
- Idempotent producer prevents retry duplicates
- Consumer commit ordering for at-least-once
- Leader-follower simplicity
- ISR and HW semantics
- Leader failover and unclean election
- Rebalance protocol (eager vs cooperative)
- Why __consumer_offsets is a topic
- DLQ for poison messages
- Hot key skew mitigations
- Quotas for noisy producers
- Increasing partition count caveats
- Memory pressure causes
```
