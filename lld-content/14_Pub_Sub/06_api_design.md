# 06 · Pub/Sub — API Design

Kafka uses a **binary protocol over TCP** for performance. Our LLD will define the logical operations. A user-facing REST gateway is also possible for low-throughput scenarios.

## Producer API (binary / RPC)

### `Produce`

```
Request:
  topic: string
  partition: int   (or null → broker-side partitioner)
  acks: "0" | "1" | "all"
  records: List<RecordBatch>
  producerId, producerEpoch, baseSequence (for idempotence)

Response:
  per-partition:
    baseOffset: long
    logAppendTime: timestamp
    error: enum
```

`acks=0` → fire-and-forget (lossy, fastest).
`acks=1` → wait for leader write (survives leader crash with durability gap).
`acks=all` → wait for all ISR (durable; latency higher).

### `FindLeader(topic)`

Returns the leader broker for each partition. Producer caches this; refreshes on `NOT_LEADER` errors.

## Consumer API

### `Subscribe(topic, group)`
Joins the consumer group; triggers a rebalance.

### `Poll(timeout)`
Returns batches of records from assigned partitions; advances internal position.

### `CommitOffset(topic, partition, offset)`
Explicit commit. Two modes:
- **sync**: blocks until ISR ack.
- **async**: fire-and-forget (faster, less safe).

### `Seek(topic, partition, offset)`
Override position (useful for replay, dead-letter handling).

## Group coordination protocol

```
JoinGroup(groupId, memberId, subscribedTopics)
   → coordinator returns groupGeneration, members, leaderId
SyncGroup(groupId, memberId, generation, [if leader: assignments])
   → coordinator distributes assignments to members
Heartbeat(groupId, memberId, generation)
   → keep alive; without it, member is evicted, triggering rebalance
LeaveGroup
   → graceful exit
```

## Admin API (REST is fine here)

```
POST   /admin/topics            { name, partitions, replicationFactor, retentionMs }
GET    /admin/topics            list
GET    /admin/topics/{name}     details (partition→leader→ISR)
DELETE /admin/topics/{name}
POST   /admin/topics/{name}/partitions { increase: int }
GET    /admin/groups            list groups
GET    /admin/groups/{id}       members + lag per partition
POST   /admin/groups/{id}/reset-offsets { partition, offset }
```

## Errors

| Code | Meaning | Client action |
| --- | --- | --- |
| `NOT_LEADER_FOR_PARTITION` | This broker is not the leader | refresh metadata, retry |
| `NOT_ENOUGH_REPLICAS` | ISR < min.isr | retry; eventual ISR catches up |
| `OFFSET_OUT_OF_RANGE` | Asked for an offset outside the log | seek to earliest/latest |
| `UNKNOWN_PRODUCER_ID` | Producer state expired | reset producer |
| `INVALID_PRODUCER_EPOCH` | Old producer fenced (transactions) | abort |
| `REBALANCE_IN_PROGRESS` | Group is rebalancing | wait + rejoin |

## Idempotency

Producer enables idempotence by setting `enable.idempotence=true`. Broker tracks last sequence per `(producerId, partition)`. Duplicate retries (same sequence) are silently deduped.

## Transactions (V2)

```
beginTransaction()
produce(...)        // to multiple partitions/topics
sendOffsetsToTxn(...) // committing offsets atomically with output writes
commitTransaction() | abortTransaction()
```

Used for stream processing: read input, write output, commit offset all-or-nothing.

## Output

```
Producer:    Produce (acks 0/1/all) + idempotence + transactions
Consumer:    Subscribe + Poll + CommitOffset + Seek + Heartbeat
Coordination: JoinGroup → SyncGroup → Heartbeat
Admin:       REST for topic/group management
Errors:      typed; client refreshes metadata on leadership errors
```
