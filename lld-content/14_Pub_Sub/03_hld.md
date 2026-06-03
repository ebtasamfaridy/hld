# 03 · Pub/Sub — High-Level Design

## Architecture

```mermaid
flowchart TB
    P1[Producer 1] -->|"publish(topic,key,val)"| LB[Producer client w/ partitioner]
    P2[Producer 2] --> LB
    LB -->|TCP to leader of partition| B1[Broker 1<br/>P0 leader, P1 follower]
    LB --> B2[Broker 2<br/>P1 leader, P2 follower]
    LB --> B3[Broker 3<br/>P2 leader, P0 follower]
    B1 <-- replicate --> B2
    B2 <-- replicate --> B3
    B3 <-- replicate --> B1

    Ctrl[Controller<br/>broker elected] -.metadata.- B1
    Ctrl -.metadata.- B2
    Ctrl -.metadata.- B3

    C1[Consumer 1] -- poll --> B1
    C2[Consumer 2] -- poll --> B2
    C3[Consumer 3] -- poll --> B3

    OffStore[(__consumer_offsets<br/>internal topic)] --- B1
```

## Roles

| Role | Responsibility |
| --- | --- |
| **Producer client** | Resolves leader for partition; batches; compresses; sends; retries |
| **Broker** | Serves a set of partition leaders + followers; persists log; replicates |
| **Controller** | One broker elected as controller; manages metadata, leader elections, ISR changes |
| **Consumer client** | Subscribes to topic; gets assignment from group coordinator; polls; commits offset |
| **Group coordinator** | A broker per consumer group; tracks members; orchestrates rebalance |

## Storage layout (per partition)

```
/data/topics/orders/partition-0/
   ├── 00000000000000000000.log         (segment 1, oldest)
   ├── 00000000000000000000.index       (sparse: offset → byte pos)
   ├── 00000000000000000000.timeindex
   ├── 00000000000000523412.log         (segment 2, current writable)
   ├── 00000000000000523412.index
   └── 00000000000000523412.timeindex
```

- **Log segment** = file capped at e.g. 1 GB or 7 days.
- **Index** = sparse offset→byte-position map (every Nth message).
- **Append**: writes only to current segment; sequential IO.
- **Read**: binary search index → seek to byte → read forward.
- **Retention**: delete entire segments older than threshold.

## Hot paths

### Publish flow

```mermaid
sequenceDiagram
    autonumber
    participant P as Producer
    participant L as Leader broker (P0)
    participant F1 as Follower (broker B)
    participant F2 as Follower (broker C)

    P->>L: PRODUCE(topic, P0, key, val) acks=all
    L->>L: append to log, get offset 1234
    par replicate
        L->>F1: fetch+append
        L->>F2: fetch+append
    end
    F1-->>L: ack
    F2-->>L: ack
    L->>L: advance high-watermark to 1234
    L-->>P: ack(offset=1234)
```

### Consume flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Consumer
    participant Coord as Group Coordinator
    participant L as Leader broker (P0)
    participant OS as Offsets store

    C->>Coord: JoinGroup
    Coord-->>C: assignment {P0, P3}
    C->>L: FETCH(P0, offset=committed)
    L-->>C: batch of records
    C->>C: process records
    C->>OS: COMMIT(P0, offset)
```

## Replication & ISR

- **ISR (in-sync replicas)** = followers caught up within `replica.lag.time.max.ms`.
- A message is **committed** when written to all ISR.
- The **high-watermark** is the highest committed offset; consumers can only read up to HW.
- If a follower falls behind, it's removed from ISR. If it catches up, it rejoins.
- Leader election: pick the next ISR member.

## Failure modes

| Failure | Handling |
| --- | --- |
| Leader broker dies | Controller picks new leader from ISR; producers retry |
| Follower lags | Removed from ISR; alert |
| All ISR die | Block writes (CP) OR allow unclean election (AP, data loss) |
| Controller dies | Brokers elect a new controller via Raft (KRaft) or ZK |
| Network split | Minority side becomes unavailable (CP) |

## Output

```
Cluster:    Producer client → Leader broker → Followers (replication)
                            ↓
                     append-only log (segmented)
                            ↓
                Consumer client (in group) ← Coordinator
Storage:    segmented files + sparse index per partition
Replication: leader-follower; ISR; HW; quorum-style commit
Failure:    controller-driven leader election; unclean election as escape hatch
```
