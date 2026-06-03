# 07 · Pub/Sub — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Domain (records) =====
    class Topic {
      <<record>>
      -String name
      -int partitions
    }
    class PartitionId {
      <<record>>
      -String topic
      -int partition
      +toString() String
    }
    class Record {
      <<record>>
      -long offset
      -Instant timestamp
      -String key
      -String value
    }

    %% ===== Storage =====
    class PartitionLog {
      -List~Record~ records
      -ReentrantLock writeLock
      -Clock clock
      -long highWatermark
      +append(key, value) long
      +read(fromOffset, maxRecords) List~Record~
      +endOffset() long
      +size() long
    }

    %% ===== Strategy: Partitioner =====
    class Partitioner {
      <<interface>>
      +partition(key, totalPartitions) int
    }
    class HashPartitioner {
      +partition(key, totalPartitions) int
    }
    class RoundRobinPartitioner {
      -AtomicInteger counter
      +partition(key, totalPartitions) int
    }
    Partitioner <|.. HashPartitioner
    Partitioner <|.. RoundRobinPartitioner

    %% ===== Broker side =====
    class MetadataService {
      -Map~String,Topic~ topics
      +createTopic(name, partitions)
      +get(name) Topic
      +all() Collection~Topic~
    }
    class Broker {
      -MetadataService metadata
      -Map~PartitionId,PartitionLog~ logs
      -GroupCoordinator coordinator
      -Clock clock
      +createTopic(name, partitions)
      +produce(topic, partition, key, value) long
      +fetch(topic, partition, fromOffset, maxRecords) List~Record~
      +endOffset(topic, partition) long
      +metadata() MetadataService
      +coordinator() GroupCoordinator
    }
    Broker o-- "1" MetadataService
    Broker o-- "1" GroupCoordinator
    Broker o-- "*" PartitionLog
    MetadataService o-- "*" Topic

    %% ===== Producer client =====
    class ProducerClient {
      -Broker broker
      -Partitioner partitioner
      +send(topicName, key, value) Metadata
    }
    class Metadata {
      <<record>>
      +String topic
      +int partition
      +long offset
    }
    ProducerClient o-- "1" Partitioner
    ProducerClient o-- "1" Broker
    ProducerClient *-- Metadata

    %% ===== Consumer side =====
    class Group {
      -String id
      -List~String~ members
      -Map~String,List~ assignments
      -Map~PartitionId,Long~ committedOffsets
      -int generation
      +id() String
      +generation() int
      +members() List~String~
      +assignmentOf(memberId) List~PartitionId~
    }
    class GroupCoordinator {
      -Map~String,Group~ groups
      +joinGroup(groupId, memberId, topics, metadata) Group
      +leaveGroup(groupId, memberId, topics, metadata)
      +commit(groupId, partition, offset)
      +committed(groupId, partition) long
    }
    class ConsumerClient {
      -String groupId
      -String memberId
      -List~String~ subscribedTopics
      -Broker broker
      -Map~PartitionId,Long~ position
      +subscribe()
      +poll(maxRecordsPerPartition) Map~PartitionId,List~Record~~
      +commit()
      +close()
    }
    GroupCoordinator o-- "*" Group
    ConsumerClient o-- "1" Broker
    ConsumerClient ..> Group
    Group o-- "*" PartitionId
```

---



## Core class diagram

```mermaid
classDiagram
    class Broker {
      -id: int
      -partitions: Map~PartitionKey,PartitionLog~
      -metadata: MetadataService
      +produce(req) ProduceResponse
      +fetch(req) FetchResponse
    }

    class PartitionLog {
      -topic: string
      -partitionId: int
      -segments: List~Segment~
      -nextOffset: long
      -highWatermark: long
      +append(records) long
      +read(offset, maxBytes) List~Record~
      +truncateTo(offset)
    }

    class Segment {
      -baseOffset: long
      -log: AppendOnlyFile
      -index: SparseIndex
      -timeIndex: TimeIndex
      +append(batch)
      +read(offset, maxBytes) List~Record~
    }

    class Partitioner {
      <<interface>>
      +partition(key, partitions) int
    }
    class HashPartitioner
    class RoundRobinPartitioner
    Partitioner <|.. HashPartitioner
    Partitioner <|.. RoundRobinPartitioner

    class ProducerClient {
      -metadata: MetadataCache
      -partitioner: Partitioner
      -batcher: RecordBatcher
      +send(topic, key, value) Future~Metadata~
    }

    class ConsumerClient {
      -group: string
      -coord: GroupCoordinator
      -fetcher: Fetcher
      -position: Map~Partition,long~
      +poll(timeout) List~Record~
      +commit()
      +seek(p, offset)
    }

    class GroupCoordinator {
      -groups: Map~string,Group~
      +join(groupId, member, topics) JoinResult
      +sync(groupId, member, assignment) SyncResult
      +heartbeat(groupId, member)
      +leave(groupId, member)
    }

    class Group {
      -id: string
      -members: List~Member~
      -generation: int
      -committedOffsets: Map~Partition,long~
      -assignments: Map~MemberId,List~Partition~~
    }

    class MetadataService {
      +createTopic(name, partitions, rf)
      +leaderOf(partition) brokerId
      +isr(partition) Set~int~
      +setLeader(partition, brokerId)
    }

    Broker o-- PartitionLog
    PartitionLog o-- Segment
    Broker o-- MetadataService
    ProducerClient o-- Partitioner
    ConsumerClient o-- GroupCoordinator
    GroupCoordinator o-- Group
```

## Package layout (`com.pubsub`)

```
domain/         Record, RecordBatch, Topic, Partition, Offset
broker/         Broker, PartitionLog, Segment, MetadataService
storage/        AppendOnlyFile, SparseIndex (or InMemoryLog for the skeleton)
consumer/       ConsumerClient, GroupCoordinator, Group
producer/       ProducerClient, Partitioner (Hash/RoundRobin), RecordBatcher
```

## Why these abstractions

### `Partitioner` as a Strategy
Producer needs pluggable partition assignment. Hash for keyed messages; round-robin for null keys; custom (geo-aware, sticky) for advanced cases. Strategy pattern is a perfect fit.

### `PartitionLog` separated from `Broker`
A broker hosts many partitions. Each `PartitionLog` is a **single-writer** structure (the leader). Locking and concurrency are per-partition, not per-broker. This is the key to Kafka's parallelism: separate locks per partition.

### `Segment` as a unit
Retention deletes whole segments. Replication ships whole segments efficiently. Compaction merges segments. Many policies operate at segment granularity, so it's its own class.

### `GroupCoordinator` as a separate abstraction
Coordination logic (join, sync, heartbeat, rebalance) is complex enough to deserve its own class, separate from the broker's storage logic. In real Kafka, the coordinator runs as a special broker role.

### `MetadataService` as an interface
Real Kafka uses KRaft (Raft-backed) for metadata. We abstract it so our skeleton can use an in-memory implementation; production swaps in KRaft.

## Output

```
Strategy:    Partitioner (hash, round-robin, custom)
Aggregate:   PartitionLog (single-writer; sequenced appends; replicated)
Component:   Segment (atomic unit for retention / replication / compaction)
Service:     GroupCoordinator (join/sync/heartbeat)
Service:     MetadataService (Raft-backed in production)
Layered:     Broker → PartitionLog → Segment
```
