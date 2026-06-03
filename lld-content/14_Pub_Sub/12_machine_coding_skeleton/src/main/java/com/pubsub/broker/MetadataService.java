package com.pubsub.broker;

import com.pubsub.domain.Topic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class MetadataService {
    private final Map<String, Topic> topics = new HashMap<>();

    public void createTopic(String name, int partitions) {
        if (topics.containsKey(name)) throw new IllegalStateException("topic exists: " + name);
        topics.put(name, new Topic(name, partitions));
    }
    public Topic get(String name)  { return topics.get(name); }
    public Collection<Topic> all() { return topics.values(); }
}
