package com.pubsub.producer;

import com.pubsub.broker.Broker;
import com.pubsub.domain.Topic;

public final class ProducerClient {
    private final Broker broker;
    private final Partitioner partitioner;

    public ProducerClient(Broker broker, Partitioner partitioner) {
        this.broker = broker; this.partitioner = partitioner;
    }

    public record Metadata(String topic, int partition, long offset) {}

    public Metadata send(String topicName, String key, String value) {
        Topic t = broker.metadata().get(topicName);
        if (t == null) throw new IllegalArgumentException("unknown topic: " + topicName);
        int p = (key == null)
                ? partitioner.partition(null, t.partitions())
                : new HashPartitioner().partition(key, t.partitions());
        long offset = broker.produce(topicName, p, key, value);
        return new Metadata(topicName, p, offset);
    }
}
