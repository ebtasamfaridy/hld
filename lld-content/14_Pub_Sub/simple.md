# Pub/Sub — Simple Notes

## 1. Core Requirements

### Functional

- Publishers send messages to topics
- Subscribers receive messages from topics
- Support multiple subscribers per topic
- Subscribers can consume asynchronously

### Non-functional

- Scalability (millions of messages)
- Fault tolerance
- Message durability (optional depending on system)
- Low latency delivery
- At-least-once or exactly-once delivery (configurable)

## 2. High-Level Components

Publisher → Broker → Topic → Subscription → Subscriber

### Components

- **Publisher**: Produces messages
- **Broker**: Central system managing routing
- **Topic**: Logical channel
- **Subscription**: Connection between topic & subscriber
- **Subscriber**: Consumes messages

## 3. Class Design (LLD)

### Message

```java
class Message {
    String id;
    String payload;
    long timestamp;
}
```

### Topic

```java
class Topic {
    String name;
    List<Subscription> subscriptions;
}
```

### Subscriber Interface

```java
interface Subscriber {
    void consume(Message message);
}
```

### Subscription

```java
class Subscription {
    Subscriber subscriber;
    Queue<Message> queue;
    boolean isActive;

    public void addMessage(Message message) {
        queue.offer(message);
    }
}
```

### Broker

```java
class Broker {
    Map<String, Topic> topics;

    public void createTopic(String topicName) {
        topics.put(topicName, new Topic(topicName));
    }

    public void publish(String topicName, Message message) {
        Topic topic = topics.get(topicName);
        for (Subscription sub : topic.subscriptions) {
            sub.addMessage(message);
        }
    }

    public void subscribe(String topicName, Subscriber subscriber) {
        Topic topic = topics.get(topicName);
        Subscription sub = new Subscription(subscriber);
        topic.subscriptions.add(sub);
    }
}
```

## 4. Message Flow

### Publish Flow

- Publisher sends message to Broker
- Broker identifies Topic
- Broker pushes message to all subscriptions
- Each subscription stores message in its queue

### Consume Flow

- Subscriber polls from its queue
- Processes message
- Sends ACK (optional)

## 5. Concurrency Design

Each subscription can have its own worker thread:

```java
class SubscriptionWorker implements Runnable {
    Subscription subscription;

    public void run() {
        while (true) {
            Message msg = subscription.queue.poll();
            if (msg != null) {
                subscription.subscriber.consume(msg);
            }
        }
    }
}
```

## 6. Delivery Guarantees

### At-most-once

- No retries
- Fast but unreliable

### At-least-once

- Retry until ACK
- Possible duplicates

### Exactly-once (complex)

- Deduplication using message IDs
- Idempotent consumers

## 7. Scaling Strategy

### Horizontal Scaling

- Partition topics
- Use multiple brokers

### Partitioning Example

- Topic: Orders
- Partitions: P1, P2, P3

Message routing:

```text
partition = hash(key) % numPartitions;
```

## 8. Fault Tolerance

- Persist messages (disk / DB)
- Replicate topics across brokers
- Leader-follower model

## 9. Enhancements (Production-Level)

- Message retention policy
- Dead Letter Queue (DLQ)
- Filtering (subscriber-level)
- Replay support
- Backpressure handling

## 10. Example Use Cases

- Notification systems
- Event-driven microservices
- Logging pipelines
- Real-time analytics
