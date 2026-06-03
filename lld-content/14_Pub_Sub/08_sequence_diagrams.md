# 08 · Pub/Sub — Sequence Diagrams

## 1. Produce flow (acks=all)

```mermaid
sequenceDiagram
    autonumber
    participant App as Application
    participant Prod as ProducerClient
    participant L as Leader broker
    participant F1 as Follower 1
    participant F2 as Follower 2

    App->>Prod: send(topic, key=K, value=V)
    Prod->>Prod: partition = hash(K) mod N
    Prod->>Prod: batch with other records
    Prod->>L: PRODUCE(topic, P, batch, acks=all, seq=N)
    L->>L: validate seq for idempotency
    L->>L: reject duplicates
    L->>L: append to log → offset 1234
    par replicate
        L->>F1: FETCH replicate
        L->>F2: FETCH replicate
    end
    F1-->>L: ack offset 1234
    F2-->>L: ack offset 1234
    L->>L: HW = 1234
    L-->>Prod: PRODUCE_RESPONSE(offset=1234)
    Prod-->>App: Future complete(metadata)
```

## 2. Producer leader-not-found (metadata refresh)

```mermaid
sequenceDiagram
    autonumber
    participant Prod as ProducerClient
    participant B1 as Stale leader (B1)
    participant Ctrl as Controller
    participant B2 as New leader (B2)

    Prod->>B1: PRODUCE(P0)
    B1-->>Prod: NOT_LEADER_FOR_PARTITION
    Prod->>Ctrl: METADATA(topic)
    Ctrl-->>Prod: P0 leader = B2
    Prod->>B2: PRODUCE(P0)
    B2-->>Prod: ack
```

## 3. Consumer poll (steady state)

```mermaid
sequenceDiagram
    autonumber
    participant C as Consumer
    participant L as Leader broker
    participant OS as Offsets store

    C->>L: FETCH(topic, P, fromOffset=committed+1, maxBytes=1MB)
    L-->>C: batch [committed+1 .. committed+B]
    C->>C: process(batch)
    C->>OS: COMMIT(group, P, committed+B)
    Note over C: position advances
```

## 4. Consumer rebalance on member join

```mermaid
sequenceDiagram
    autonumber
    participant C1 as Existing consumer
    participant C2 as New consumer
    participant Coord as Group Coordinator

    C2->>Coord: JoinGroup(groupId)
    Coord->>C1: REBALANCE_IN_PROGRESS (on next heartbeat)
    C1->>Coord: JoinGroup(groupId, prev memberId)
    Coord->>Coord: pick group leader = C1
    Coord-->>C1: members [C1, C2], generation N+1
    C1->>C1: assign({P0,P1} → C1, {P2,P3} → C2)
    C1->>Coord: SyncGroup(N+1, assignments)
    Coord-->>C1: assignment {P0, P1}
    Coord-->>C2: assignment {P2, P3}
    C1->>C1: revoke nothing (kept)
    C2->>C2: start fetching P2, P3
```

## 5. Leader failover

```mermaid
sequenceDiagram
    autonumber
    participant L as Leader (B1) [dies]
    participant Ctrl as Controller
    participant F1 as Follower (B2)
    participant F2 as Follower (B3)
    participant Prod as Producer

    Note over L: process crashes
    Ctrl->>Ctrl: detect missing heartbeat
    Ctrl->>Ctrl: pick new leader from ISR (B2)
    Ctrl->>F1: BECOME_LEADER(P0)
    Ctrl->>F2: BECOME_FOLLOWER(of B2 for P0)
    Prod->>L: PRODUCE → connection refused
    Prod->>Ctrl: METADATA refresh
    Ctrl-->>Prod: P0 leader = B2
    Prod->>F1: PRODUCE
    F1-->>Prod: ack
```

## 6. Idempotent retry (duplicate produce)

```mermaid
sequenceDiagram
    autonumber
    participant Prod as Producer
    participant L as Leader

    Prod->>L: PRODUCE(seq=42)
    L->>L: append → offset 1000
    L--xProd: ack lost (network)
    Prod->>L: PRODUCE(seq=42) [retry]
    L->>L: seq 42 already seen for this producerId
    L-->>Prod: ack(offset=1000) [no double-write]
```

## Output

```
Produce:   batch → leader → replicate to ISR → HW advance → ack
Consume:   poll batch → process → commit offset
Rebalance: JoinGroup → SyncGroup; cooperative (sticky) keeps assignments stable
Failover: controller-driven; producer retries with metadata refresh
Idempot.: producerId + sequence dedup at broker
```
