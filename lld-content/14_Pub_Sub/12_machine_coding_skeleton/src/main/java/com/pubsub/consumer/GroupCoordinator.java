package com.pubsub.consumer;

import com.pubsub.broker.MetadataService;
import com.pubsub.domain.PartitionId;
import com.pubsub.domain.Topic;

import java.util.*;

public final class GroupCoordinator {
    private final Map<String, Group> groups = new HashMap<>();

    public Group joinGroup(String groupId, String memberId, List<String> subscribedTopics,
                            MetadataService metadata) {
        Group g = groups.computeIfAbsent(groupId, Group::new);
        g.addMember(memberId);
        rebalance(g, subscribedTopics, metadata);
        return g;
    }

    public void leaveGroup(String groupId, String memberId,
                           List<String> subscribedTopics, MetadataService metadata) {
        Group g = groups.get(groupId);
        if (g == null) return;
        g.removeMember(memberId);
        rebalance(g, subscribedTopics, metadata);
    }

    public long committed(String groupId, PartitionId p) {
        Group g = groups.get(groupId);
        return g == null ? -1L : g.committed(p);
    }

    public void commit(String groupId, PartitionId p, long offset) {
        Group g = groups.get(groupId);
        if (g == null) return;
        g.commit(p, offset);
    }

    private void rebalance(Group g, List<String> topics, MetadataService metadata) {
        List<PartitionId> all = new ArrayList<>();
        for (String t : topics) {
            Topic topic = metadata.get(t);
            if (topic == null) continue;
            for (int p = 0; p < topic.partitions(); p++) all.add(new PartitionId(t, p));
        }
        g.rebalance(all);
    }
}
