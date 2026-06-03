package com.pubsub.producer;

public final class HashPartitioner implements Partitioner {
    @Override public int partition(String key, int totalPartitions) {
        if (key == null) return 0;
        int h = key.hashCode();
        return Math.floorMod(h, totalPartitions);
    }
}
