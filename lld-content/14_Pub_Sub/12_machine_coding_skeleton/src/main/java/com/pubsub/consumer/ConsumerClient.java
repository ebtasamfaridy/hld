package com.pubsub.consumer;

import com.pubsub.broker.Broker;
import com.pubsub.domain.PartitionId;
import com.pubsub.domain.Record;

import java.util.*;

public final class ConsumerClient {
    private final String memberId;
    private final String groupId;
    private final List<String> topics;
    private final Broker broker;
    /** Local position per partition: next offset to fetch. */
    private final Map<PartitionId, Long> position = new HashMap<>();

    public ConsumerClient(String memberId, String groupId, List<String> topics, Broker broker) {
        this.memberId = memberId; this.groupId = groupId; this.topics = topics; this.broker = broker;
    }

    public String memberId() { return memberId; }

    public Group subscribe() {
        return broker.coordinator().joinGroup(groupId, memberId, topics, broker.metadata());
    }

    public void leave() {
        broker.coordinator().leaveGroup(groupId, memberId, topics, broker.metadata());
    }

    public Map<PartitionId, List<Record>> poll(int maxRecordsPerPartition) {
        Group g = broker.coordinator().joinGroup(groupId, memberId, topics, broker.metadata());
        Map<PartitionId, List<Record>> out = new LinkedHashMap<>();
        for (PartitionId p : g.assignmentOf(memberId)) {
            long start = position.getOrDefault(p,
                    Math.max(0, broker.coordinator().committed(groupId, p) + 1));
            List<Record> recs = broker.fetch(p.topic(), p.partition(), start, maxRecordsPerPartition);
            if (!recs.isEmpty()) {
                position.put(p, recs.get(recs.size() - 1).offset() + 1);
            }
            out.put(p, recs);
        }
        return out;
    }

    /** Commit positions for all assigned partitions that have advanced. */
    public void commit() {
        for (Map.Entry<PartitionId, Long> e : position.entrySet()) {
            broker.coordinator().commit(groupId, e.getKey(), e.getValue() - 1);
        }
    }
}
