# 12 · Pub/Sub — Machine Coding Skeleton

In-memory single-broker Pub/Sub with topics, partitions, consumer groups, offsets, and rebalance.

```
src/main/java/com/pubsub/
├── domain/      Record, Topic, PartitionId
├── storage/     PartitionLog (in-memory append-only)
├── broker/      Broker, MetadataService
├── producer/    ProducerClient, Partitioner (Hash, RoundRobin)
├── consumer/    ConsumerClient, GroupCoordinator, Group
└── Main.java
```

## Demo
1. Create topic `orders` with 3 partitions.
2. Producer publishes 10 keyed records → distributed by hash.
3. ConsumerGroup `g1` with 1 consumer → consumes all 3 partitions.
4. Add a 2nd consumer → rebalance assigns partitions 2 ↔ 1.
5. Crash one consumer → rebalance back to 1 consumer.
6. Show committed offsets persist across reconnect.
