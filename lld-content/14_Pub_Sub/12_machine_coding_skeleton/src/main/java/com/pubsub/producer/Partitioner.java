package com.pubsub.producer;

public interface Partitioner {
    int partition(String key, int totalPartitions);
}
