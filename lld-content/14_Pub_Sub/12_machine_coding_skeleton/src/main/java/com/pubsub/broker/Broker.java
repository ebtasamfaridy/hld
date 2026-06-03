package com.pubsub.broker;

import com.pubsub.consumer.GroupCoordinator;
import com.pubsub.domain.PartitionId;
import com.pubsub.domain.Record;
import com.pubsub.storage.PartitionLog;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Broker {

    private final MetadataService metadata = new MetadataService();
    private final Map<PartitionId, PartitionLog> logs = new HashMap<>();
    private final GroupCoordinator coordinator = new GroupCoordinator();
    private final Clock clock;

    public Broker(Clock clock) { this.clock = clock; }

    public MetadataService metadata()    { return metadata; }
    public GroupCoordinator coordinator() { return coordinator; }

    public void createTopic(String name, int partitions) {
        metadata.createTopic(name, partitions);
        for (int p = 0; p < partitions; p++) {
            logs.put(new PartitionId(name, p), new PartitionLog(clock));
        }
    }

    /** Produce a single record to (topic, partition); returns assigned offset. */
    public long produce(String topic, int partition, String key, String value) {
        PartitionLog log = logs.get(new PartitionId(topic, partition));
        if (log == null) throw new IllegalArgumentException("no such partition");
        return log.append(key, value);
    }

    /** Fetch up to maxRecords from (topic, partition) starting at fromOffset. */
    public List<Record> fetch(String topic, int partition, long fromOffset, int maxRecords) {
        PartitionLog log = logs.get(new PartitionId(topic, partition));
        if (log == null) return List.of();
        return log.read(fromOffset, maxRecords);
    }

    public long endOffset(String topic, int partition) {
        PartitionLog log = logs.get(new PartitionId(topic, partition));
        return log == null ? 0 : log.endOffset();
    }
}
