package com.pubsub.consumer;

import com.pubsub.domain.PartitionId;

import java.util.*;

/** Group state managed by the coordinator. */
public final class Group {
    private final String id;
    private final List<String> members = new ArrayList<>(); // memberId order
    private final Map<String, List<PartitionId>> assignments = new HashMap<>();
    private final Map<PartitionId, Long> committedOffsets = new HashMap<>();
    private int generation = 0;

    public Group(String id) { this.id = id; }

    public String id() { return id; }
    public int generation() { return generation; }
    public List<String> members() { return List.copyOf(members); }
    public List<PartitionId> assignmentOf(String memberId) {
        return assignments.getOrDefault(memberId, List.of());
    }

    void addMember(String memberId) {
        if (!members.contains(memberId)) members.add(memberId);
    }
    void removeMember(String memberId) {
        members.remove(memberId);
        assignments.remove(memberId);
    }
    void rebalance(List<PartitionId> all) {
        generation++;
        assignments.clear();
        if (members.isEmpty() || all.isEmpty()) return;

        // Range-style assignment for simplicity (Kafka default).
        Collections.sort(members);
        List<PartitionId> sortedPs = new ArrayList<>(all);
        sortedPs.sort(Comparator.<PartitionId, String>comparing(PartitionId::topic)
                .thenComparingInt(PartitionId::partition));
        int n = members.size();
        int total = sortedPs.size();
        int per = total / n;
        int extra = total % n;
        int idx = 0;
        for (int i = 0; i < n; i++) {
            int chunk = per + (i < extra ? 1 : 0);
            assignments.put(members.get(i), new ArrayList<>(sortedPs.subList(idx, idx + chunk)));
            idx += chunk;
        }
    }

    /** Committed offset (or -1 if never committed → start at earliest). */
    long committed(PartitionId p) {
        return committedOffsets.getOrDefault(p, -1L);
    }

    void commit(PartitionId p, long offset) {
        committedOffsets.put(p, offset);
    }
}
