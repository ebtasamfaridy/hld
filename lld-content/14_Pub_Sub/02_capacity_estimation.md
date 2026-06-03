# 02 · Pub/Sub — Capacity Estimation

## Cluster scale (mid-size)

```
Brokers:                 6
Topics:                  500
Partitions per topic:    10 (avg) → 5 K total partitions
Replication factor:      3
Replicas total:          5 K × 3 = 15 K
Partitions per broker:   ~2.5 K (with replicas)
```

## Throughput

```
Publish rate:    300 K msg/sec cluster-wide
Per broker:      ~50 K msg/sec (3 acks + leader work)
Avg msg size:    1 KB
Bytes / sec:     300 MB/s ingest
Bytes / sec out: 300 MB/s × #consumers (often 1–3)
```

## Storage

```
Daily ingest:    300 MB/s × 86 400 = ~25 TB/day
RF×3:            75 TB/day total disk
Retention 7 d:   525 TB cluster-wide → ~88 TB/broker
```

This explains why **brokers have giant disks** (or tiered storage to S3).

## Network

```
Per broker NIC:  10 GbE = ~1.25 GB/s
Cluster total:   75 GB/s
Replica traffic: 2× publish (each follower replicates leader) = 600 MB/s extra
```

## What forces the design

1. **Sequential disk writes** are the only way to hit 100 K msg/s/broker. Random IO kills it.
2. **Partitions are the parallelism unit.** More partitions = more concurrent writers but more bookkeeping.
3. **Replication adds 2× disk + 2× network** per message.
4. **Consumer offsets** are tiny but read-frequent — keep them in a special internal topic.
5. **Metadata** (topic→partition→leader map) is small; controller keeps it cached cluster-wide.

## Hot bottlenecks

| Bottleneck | Mitigation |
| --- | --- |
| Single-partition hot key | Increase partition count; rebalance keys |
| Slow consumer in a group | Increase consumer count up to partition count |
| Broker out of disk | Earlier retention; tiered storage to S3 |
| Network saturation | Compress messages (LZ4 / Snappy) |

## Output

```
Cluster:    6 brokers, 500 topics, 5 K partitions × RF 3 = 15 K replicas
Throughput: 300 K msg/s cluster, ~50 K/broker, 300 MB/s ingest
Storage:    25 TB/day ingest × RF3 × 7d = 525 TB
Pattern:    sequential disk + partition parallelism + replication + per-broker SSD/HDD
```
