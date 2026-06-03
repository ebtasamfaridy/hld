# 09 · Pub/Sub — State Machines

## Partition replica state (per broker holding it)

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> FOLLOWER : initial assignment as follower
    NEW --> LEADER   : initial assignment as leader
    FOLLOWER --> LEADER  : controller promotes (failover)
    LEADER --> FOLLOWER  : controller demotes (rebalance)
    FOLLOWER --> OFFLINE : disk full / failure
    LEADER --> OFFLINE   : disk full / failure
    OFFLINE --> FOLLOWER : recovered, catching up
    FOLLOWER --> ISR     : caught-up within threshold
    ISR --> FOLLOWER     : fell behind
```

`ISR` is a substate of `FOLLOWER` that means "caught up enough to acknowledge writes."

## Producer (idempotent) state per partition

```mermaid
stateDiagram-v2
    [*] --> INIT_PRODUCER_ID : init
    INIT_PRODUCER_ID --> NORMAL : got producerId
    NORMAL --> NORMAL          : send next sequence
    NORMAL --> RETRYING        : send failed
    RETRYING --> NORMAL        : ack received
    RETRYING --> EXPIRED       : delivery.timeout exceeded
    NORMAL --> FENCED          : new producer with same txn id
    FENCED --> [*]
```

## Consumer state in a group

```mermaid
stateDiagram-v2
    [*] --> JOINING : Subscribe / first poll
    JOINING --> SYNCING : JoinGroup ack
    SYNCING --> CONSUMING : SyncGroup ack with assignment
    CONSUMING --> CONSUMING : poll, commit
    CONSUMING --> REBALANCING : rebalance triggered
    REBALANCING --> JOINING
    CONSUMING --> LEAVING : Close()
    LEAVING --> [*]
    CONSUMING --> EVICTED : missed heartbeat / session timeout
    EVICTED --> JOINING : reconnect
```

## Group state (coordinator's view)

```mermaid
stateDiagram-v2
    [*] --> EMPTY
    EMPTY --> PREPARING_REBALANCE : member joins
    PREPARING_REBALANCE --> COMPLETING_REBALANCE : all known members joined
    COMPLETING_REBALANCE --> STABLE : all SyncGroup acks received
    STABLE --> PREPARING_REBALANCE : member leaves / new joins / heartbeat timeout
    STABLE --> DEAD : last member leaves
    DEAD --> [*]
```

## Topic / partition lifecycle (admin)

```mermaid
stateDiagram-v2
    [*] --> CREATING : createTopic
    CREATING --> ACTIVE : leaders assigned
    ACTIVE --> EXPANDING : addPartitions
    EXPANDING --> ACTIVE
    ACTIVE --> DELETING : deleteTopic
    DELETING --> [*]
```

Note: adding partitions changes the partitioner output for new keys but **does not rebalance existing data**. Old keys may now go to a different partition. This is a known caveat.

## Transaction state (V2)

```mermaid
stateDiagram-v2
    [*] --> EMPTY
    EMPTY --> ONGOING : beginTxn
    ONGOING --> COMMITTING : commitTxn
    ONGOING --> ABORTING   : abortTxn
    COMMITTING --> COMPLETE
    ABORTING --> COMPLETE
    COMPLETE --> EMPTY : next txn
```

## Output

```
Replica:   FOLLOWER ↔ ISR; FOLLOWER ↔ LEADER on failover; OFFLINE on failure
Producer:  INIT → NORMAL → RETRY → fenced/expired
Consumer:  JOIN → SYNC → CONSUME → REBALANCE on changes
Group:     EMPTY → PREPARING → COMPLETING → STABLE
Topic:     CREATING → ACTIVE → DELETING
```
