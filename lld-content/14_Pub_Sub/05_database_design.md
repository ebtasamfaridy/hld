# 05 · Pub/Sub — Storage Design

This system's "database" is **the log itself** — files on disk. We don't use Postgres for the message stream. Metadata uses a small KV store (KRaft / ZooKeeper).

## On-disk layout per broker

```
/data/
├── topics/
│   ├── orders/
│   │   ├── partition-0/
│   │   │   ├── 00000000000000000000.log
│   │   │   ├── 00000000000000000000.index
│   │   │   ├── 00000000000000000000.timeindex
│   │   │   ├── 00000000000000523412.log    ← active
│   │   │   ├── 00000000000000523412.index
│   │   │   └── 00000000000000523412.timeindex
│   │   └── partition-1/...
│   └── payments/...
├── meta.properties           (broker id, version)
├── recovery-point-offset
└── log-start-offset          (after retention deletes)
```

## Segment file format (logical view)

Each `.log` file is a sequence of record batches:

```
+------------------------------------------------------+
| baseOffset (8B) | batchLength (4B) | partitionLeaderEpoch (4B) |
| magic (1B)      | crc (4B)         | attributes (2B)          |
| firstTimestamp  | maxTimestamp     | producerId | producerEpoch |
| baseSequence    | recordCount (4B)                              |
| record[0]                                                       |
| record[1]                                                       |
| ...                                                             |
+------------------------------------------------------+
```

Each `record` carries:
```
length, attributes, timestampDelta, offsetDelta, keyLen, key, valueLen, value, headers
```

Why this nested structure?
- **Batching** amortizes the per-record overhead (timestamps, sequence numbers) across many records.
- **Compression** is per-batch (LZ4 / Snappy) for ratio + decompress speed.
- **CRC** ensures integrity of the whole batch.

## Index files

`.index` — sparse `(relativeOffset → bytePosition)` map. Entry every ~4 KB of log.
`.timeindex` — sparse `(timestamp → relativeOffset)` map for time-based seeks.

Sparse index → small (megabytes for terabyte logs). Find an offset:
1. Binary-search index for the largest entry ≤ target offset.
2. Seek to that byte, scan forward through batches until you hit the target.

## Append (writer side)

```
1. fsync policy: every N records or every M ms (configurable)
2. Append to active segment file (sequential write).
3. If active segment > segment.bytes (e.g., 1 GB), roll: open new segment.
4. Update in-memory nextOffset.
5. Replicate to followers: fetch-based pull model.
```

## Retention

Two strategies (per topic):
1. **Time-based**: delete segments where `maxTimestamp < now - retention.ms`.
2. **Size-based**: delete oldest segments while total size > `retention.bytes`.

Deletion is a **whole-segment unlink**. We never modify the inside of a segment.

## Compacted topics (V2)

For topics where we want only the latest message per key (e.g., "current state of user X"):
1. Background "log cleaner" merges old segments.
2. For each key, keep only the latest record.
3. Compaction runs on closed segments only — never touches the active write head.
4. Tombstones (`value=null`) mark deletes; eventually removed.

## Metadata store

Topic config, partition→broker mapping, ISR, controller election → small KV store.
- Old: ZooKeeper.
- New: KRaft (Kafka uses Raft internally).

For our LLD, model it as a `MetadataService` with operations:
- `createTopic(name, partitions, rf)`
- `getLeader(topic, partition)`
- `getISR(topic, partition)`
- `setLeader(topic, partition, brokerId)`

## __consumer_offsets (internal topic)

Committed offsets per `(group, topic, partition)` are themselves stored in a special compacted topic `__consumer_offsets`. This way:
- Replicated.
- Compacted (only latest offset per key).
- Fast read on consumer restart.

## Why files instead of a DB?

| Criterion | Files (log) | DB |
| --- | --- | --- |
| Append throughput | very high (sequential) | bottlenecked by indexes |
| Retention | unlink whole segment | delete + vacuum |
| Random access | only via offset (rare) | strong |
| Cost | low | higher |

Our access pattern is **sequential-append + sequential-read**. Files dominate.

## Output

```
Per partition:    segmented append-only log files + sparse offset/time indexes
Append:           sequential, batched, optional compression, fsync policy
Retention:        time / size based whole-segment delete
Metadata:         small KV (KRaft / ZooKeeper) — leader, ISR, configs
Offsets:          stored in compacted internal topic
```
