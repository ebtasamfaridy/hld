package com.pubsub;

import com.pubsub.broker.Broker;
import com.pubsub.consumer.ConsumerClient;
import com.pubsub.consumer.Group;
import com.pubsub.domain.PartitionId;
import com.pubsub.domain.Record;
import com.pubsub.producer.ProducerClient;
import com.pubsub.producer.HashPartitioner;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        Broker broker = new Broker(Clock.systemUTC());
        broker.createTopic("orders", 3);

        ProducerClient producer = new ProducerClient(broker, new HashPartitioner());
        for (int i = 0; i < 9; i++) {
            String key = "user-" + (i % 4);
            var md = producer.send("orders", key, "order#" + i);
            System.out.printf("  produce key=%s → %s offset=%d%n", key, md.partition(), md.offset());
        }

        section("Consumer C1 alone in group g1");
        ConsumerClient c1 = new ConsumerClient("c1", "g1", List.of("orders"), broker);
        Group g = c1.subscribe();
        System.out.println("  c1 assigned: " + g.assignmentOf("c1"));
        consumeAndCommit(c1);

        section("Add Consumer C2 → rebalance");
        ConsumerClient c2 = new ConsumerClient("c2", "g1", List.of("orders"), broker);
        g = c2.subscribe();
        System.out.println("  c1 assigned: " + g.assignmentOf("c1"));
        System.out.println("  c2 assigned: " + g.assignmentOf("c2"));

        section("Produce 6 more, both consume");
        for (int i = 9; i < 15; i++) {
            producer.send("orders", "user-" + (i % 4), "order#" + i);
        }
        consumeAndCommit(c1);
        consumeAndCommit(c2);

        section("C1 leaves → C2 takes everything");
        c1.leave();
        g = c2.subscribe();
        System.out.println("  c2 assigned: " + g.assignmentOf("c2"));
    }

    private static void consumeAndCommit(ConsumerClient c) {
        Map<PartitionId, List<Record>> batch = c.poll(100);
        for (var e : batch.entrySet()) {
            for (Record r : e.getValue()) {
                System.out.printf("  %s consumed %s offset=%d key=%s value=%s%n",
                        c.memberId(), e.getKey(), r.offset(), r.key(), r.value());
            }
        }
        c.commit();
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
