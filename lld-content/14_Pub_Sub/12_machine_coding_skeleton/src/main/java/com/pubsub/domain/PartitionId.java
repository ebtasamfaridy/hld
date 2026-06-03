package com.pubsub.domain;

public record PartitionId(String topic, int partition) {
    @Override public String toString() { return topic + "-" + partition; }
}
