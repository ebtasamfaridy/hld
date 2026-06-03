package com.pubsub.producer;

import java.util.concurrent.atomic.AtomicInteger;

public final class RoundRobinPartitioner implements Partitioner {
    private final AtomicInteger counter = new AtomicInteger();
    @Override public int partition(String key, int totalPartitions) {
        return Math.floorMod(counter.getAndIncrement(), totalPartitions);
    }
}
