# 06 · Concurrency in LLD — Locks, CAS, Idempotency, Races

> Concurrency is the topic where most LLD candidates lose points. Master it and you separate yourself.

---

## The Mental Model

Two things mutate the same state at the same time. The result depends on the timing. **That's a race condition.**

To prevent it, you must:

1. **Detect** — recognize where shared mutable state exists.
2. **Choose a discipline** — locking, optimistic, CAS, idempotency, message queues.
3. **Encode it** — at the right layer (DB, app, queue).

The four levers, ordered cheapest → most expensive:

```
1. Idempotency keys + UNIQUE constraints   ← almost free, very effective
2. Optimistic locking (version columns)    ← cheap, default choice
3. CAS / atomic ops (Redis, atomic CPU ops) ← cheap, low-level
4. Pessimistic locking (SELECT ... FOR UPDATE) ← correct, but slow & deadlock-prone
```

Always start at #1 and only escalate when needed.

---

## 1. Race Conditions — Where They Hide

### Race 1: Lost update

Two requests read the same row, both increment, both write back. The second overwrites the first.

```
T1:  read  balance=100
T2:  read  balance=100
T1:  write balance=110   (deposit 10)
T2:  write balance=120   (deposit 20)        ← user actually deposited 30, sees 120
```

### Race 2: Double booking

Two users try to book the **last hotel room** simultaneously.

```
T1:  SELECT inventory ... 1 left
T2:  SELECT inventory ... 1 left
T1:  CREATE booking for date X
T2:  CREATE booking for date X    ← both succeed; hotel oversold
```

### Race 3: Phantom read / inventory under-decrement

You count items in stock, then decrement. Between count and decrement, someone else sold one.

### Race 4: Idempotency replay (network retry)

Client retries on timeout. The server processed the first call but the response was lost. Now you have **two orders**.

---

## 2. Idempotency — The First Line of Defense

> **Idempotent operation:** running it 1 or N times yields the same observable state.

This is the **cheapest** safety mechanism and you should use it everywhere.

### Pattern: Idempotency key + UNIQUE constraint

```sql
ALTER TABLE orders
  ADD COLUMN idempotency_key VARCHAR(80) UNIQUE;
```

```java
public Order placeOrder(PlaceOrderCommand cmd) {
  return orderRepository.findByIdempotencyKey(cmd.idempotencyKey())
    .orElseGet(() -> {
      Order o = new Order(cmd);
      try {
        return orderRepository.save(o);
      } catch (DuplicateKeyException e) {        // race lost
        return orderRepository.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow();
      }
    });
}
```

The DB's UNIQUE constraint **enforces** at-most-once. The application falls back to reading the existing row.

### Idempotency for non-creation operations

For PUT-style updates (`PUT /orders/{id}/cancel`), idempotency is "natural" if cancellation is a transition that's a no-op when already cancelled.

For POST-style mutations, **always require an idempotency key**. Most production systems do (Stripe, AWS).

### Subtle bug: same key, different payload

If a client retries with the same key but slightly different payload (e.g., a new item added), you must:

- Either **accept** the request (idempotent — return same result).
- Or **reject** (return 409 with the original payload's hash).

Stripe rejects. AWS sometimes accepts. **Document your contract.** In an interview, lean toward rejection — it's safer.

---

## 3. Optimistic Locking

> Read with a version number. On write, atomically check the version still matches. If not, retry.

### Why "optimistic"?

You assume conflict is rare. You don't take a lock; you detect the conflict at write time.

### Schema

```sql
CREATE TABLE orders (
  id      UUID PRIMARY KEY,
  status  VARCHAR(20),
  version BIGINT NOT NULL DEFAULT 0,
  ...
);
```

### Java implementation

```java
public Order updateStatus(UUID id, OrderStatus next) {
  for (int attempt = 0; attempt < 3; attempt++) {
    Order o = orderRepository.findById(id).orElseThrow();
    o.transitionTo(next);                            // domain check
    long previous = o.version();
    o.bumpVersion();
    int rows = jdbc.update(
      "UPDATE orders SET status = ?, version = ? WHERE id = ? AND version = ?",
      o.status().name(), o.version(), id, previous);
    if (rows == 1) return o;
    // version mismatch — someone updated between our read and write
  }
  throw new OptimisticLockException(id);
}
```

The `WHERE version = ?` is the **CAS at SQL level**. If 0 rows update, we lost the race.

### When to retry vs surface the error

- For **idempotent transitions** (e.g., setting `status = CANCELLED` on already-CANCELLED) — retry/no-op.
- For **state-machine transitions** — re-read state and re-validate. Retry up to 3 times. Fail loudly otherwise.
- For **money** — never silently retry. Surface the conflict.

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| No locks held, high concurrency | Wasted work on conflict |
| Easy in any RDBMS | Need to design retry policy |
| Detects all conflicts | Bad fit if conflict rate is high |

**Default to optimistic locking.** Use pessimistic only when conflicts are >30% or the work between read and write is expensive to redo.

---

## 4. Pessimistic Locking

> Take the lock at read time; release at commit/rollback.

### SQL

```sql
BEGIN;
SELECT * FROM hotel_rooms
WHERE hotel_id = ? AND room_type = ? AND date = ?
FOR UPDATE;
-- inventory now exclusively locked
UPDATE hotel_rooms SET available = available - 1 WHERE ...;
INSERT INTO bookings ...;
COMMIT;
```

### When to use

- **Inventory decrement** under high contention (last-room-on-the-floor).
- **Wallet debit** if the same wallet sees many concurrent debits.
- **Scheduler / task queue** picking next job.

### Risks

| Risk | Mitigation |
| --- | --- |
| Deadlock | Always lock rows in a deterministic order (e.g., sorted by id) |
| Long transactions block others | Keep txn small; do IO outside txn |
| `FOR UPDATE` over many rows = scan-lock | Add proper index |
| Application crash with held lock | DB session timeout + idempotent retries |

### `FOR UPDATE SKIP LOCKED`

A worker queue pattern. Workers pick the next free row without blocking on others.

```sql
SELECT * FROM tasks
WHERE status = 'PENDING'
ORDER BY created_at
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

We use this in dispatch / matching engines.

---

## 5. Compare-And-Swap (CAS)

> An atomic primitive: "set X to V2 if and only if X is currently V1."

CAS underlies optimistic locking, lock-free data structures, distributed counters.

### Java level

```java
private final AtomicLong driverCount = new AtomicLong();

driverCount.compareAndSet(prev, prev + 1);
// or simpler:
driverCount.incrementAndGet();
```

### Redis level

```
WATCH key
val = GET key
MULTI
SET key new_val
EXEC          ← fails if key changed since WATCH
```

Or with Lua scripts (atomic by definition):

```lua
local v = tonumber(redis.call('GET', KEYS[1]))
if v >= ARGV[1] then
  redis.call('DECRBY', KEYS[1], ARGV[1])
  return 1
end
return 0
```

This is how to implement **inventory decrement in Redis** without races.

### DB level

CAS via UPDATE WHERE:

```sql
UPDATE inventory
SET   available = available - 1
WHERE sku = ? AND available > 0;
```

This atomically decrements *and* checks — one statement, one round trip, no lock needed.

### CAS vs locks

| | CAS | Lock |
| --- | --- | --- |
| Throughput under low contention | Excellent | Good |
| Throughput under high contention | Wastes CPU on retries | Serializes (slower) |
| Code simplicity | More logic | Less logic |
| Deadlock risk | None | Yes |

---

## 6. Distributed Locks

When you need a lock **across processes** (e.g., two app servers booking inventory).

### Options

| Option | Use when |
| --- | --- |
| Redis SET NX with TTL (Redlock-ish) | Need fast, best-effort lock |
| Database row lock (`FOR UPDATE`) | DB is your source of truth anyway |
| ZooKeeper / etcd | Need strong consensus |
| Postgres advisory locks (`pg_advisory_lock`) | App-level mutex on Postgres |

### Pattern: "lease" lock

```
SET resource_key=owner_id NX EX 30
```

- `NX` = only if not exists (acquire).
- `EX 30` = 30-second lease (auto-release if owner crashes).

Release atomically with Lua:

```lua
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
else
  return 0
end
```

This avoids accidentally releasing **someone else's** lock if you've timed out.

### Caveats

- Don't rely on Redis locks for **money**. They're not strongly consistent (despite Redlock's claims).
- For correctness-critical paths, prefer DB-backed locks.

---

## 7. Concurrency Patterns by System

| System | Where the race is | Solution |
| --- | --- | --- |
| Hotel booking | Last room on date X | DB CAS: `UPDATE WHERE available > 0`, plus UNIQUE on (hotel,room,date) when modeling slots |
| Food delivery dispatch | Two dispatchers assign same driver | DB row lock on `drivers WHERE status='IDLE' FOR UPDATE SKIP LOCKED` |
| Ride matching | Driver accepts two rides | App-level state machine + optimistic lock; first valid wins |
| Library borrow | Last copy borrowed twice | DB CAS on `copies.status='AVAILABLE'` |
| Splitwise expense add | Concurrent edits change balance | Optimistic lock on Expense + transactional balance update |
| Payment retry | Network retry creates double charge | Idempotency key + UNIQUE constraint |

---

## 8. Thread Safety Patterns

### Immutability

The simplest concurrency strategy: if state can't change, no race.

```java
public final class Money {
  private final BigDecimal amount;
  private final Currency currency;
  // no setters, all methods return new Money
}
```

Default to immutable value objects (Money, Address, Coordinates, OrderStatus).

### Confinement

Confine mutable state to **one thread** (event loop), or **one request** (request-scoped).

### Coarse-grained sync (when nothing else fits)

```java
public class InventoryCache {
  private final Map<String, Integer> stock = new ConcurrentHashMap<>();
  public boolean reserve(String sku) {
    return stock.compute(sku, (k, v) -> v == null || v == 0 ? v : v - 1) != null;
  }
}
```

`ConcurrentHashMap.compute()` is atomic per key. Avoid `synchronized (this)` over the whole map.

### Read-write locks

For caches with many readers, few writers:

```java
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
```

Useful but easy to misuse. Prefer `ConcurrentHashMap` / immutable copy-on-write where possible.

---

## 9. The "Saga" Pattern for Distributed Transactions

When a flow spans multiple services, you can't use a single DB transaction. Use a **saga**:

1. Each step is a local transaction.
2. Each step has a compensating action.
3. On failure, run compensations in reverse order.

```
Order.placed
  ├── 1. reserveInventory      (compensate: releaseInventory)
  ├── 2. chargePayment         (compensate: refundPayment)
  ├── 3. assignDriver          (compensate: unassignDriver)
  └── 4. confirmOrder
```

If step 3 fails, compensate steps 2 and 1.

### Implementation styles

- **Orchestration** (a coordinator drives steps).
- **Choreography** (services react to each other's events).

Orchestration is easier to reason about; choreography is more decoupled.

---

## 10. Practical Cheat Sheet

| You see... | You should reach for... |
| --- | --- |
| External mutation API | Idempotency key + UNIQUE |
| Read-modify-write on a row | Optimistic lock (`version`) |
| Decrement inventory | DB CAS `UPDATE WHERE x > 0` |
| Pick next task in queue | `FOR UPDATE SKIP LOCKED` |
| Counter across processes | Redis `INCR` / DB sequence |
| Cross-service workflow | Saga + Outbox pattern |
| Fan-out with retries | Async queue + idempotent consumer |
| Cache + DB consistency | Cache-aside with TTL or write-through |

---

## 11. The Outbox Pattern

When you must **publish an event** and **commit a DB change** atomically.

### Bad

```java
db.save(order);
eventBus.publish(orderPlacedEvent);    // ← if process dies between, event is lost
```

### Good

```java
db.beginTransaction();
db.save(order);
db.insertInto("outbox", { event: orderPlacedEvent });
db.commit();

// background poller reads outbox and publishes to Kafka, deletes row on ack
```

The DB transaction guarantees the event row is durable iff the order is durable. A separate poller publishes from outbox to Kafka and marks the row delivered.

This is the **canonical solution** for "save-and-publish atomically."

---

## 12. Race Conditions Specific to LLD Interviews

### a) Hotel double-booking

```sql
INSERT INTO room_inventory (hotel, room_type, date, total_avail)
VALUES (...);    -- pre-populated for next 365 days

-- Atomic decrement on book:
UPDATE room_inventory
SET total_avail = total_avail - 1
WHERE hotel=? AND room_type=? AND date=? AND total_avail > 0;
```

If 0 rows updated, no inventory.

### b) Library — last copy borrowed twice

```sql
UPDATE book_copies
SET status='BORROWED', borrower_id=?
WHERE id=? AND status='AVAILABLE';

-- 0 rows → someone else got it
```

### c) Splitwise — concurrent expense edits

Two users edit the same expense. Use optimistic lock on `expenses.version`. The second edit gets a 409.

### d) Ride dispatch — two riders for same driver

State machine on driver: `IDLE → ASSIGNED`. Use optimistic lock or `UPDATE WHERE status='IDLE'`.

### e) Wallet debit / Splitwise settlement

Use a `version` column or `DEBIT WHERE balance >= amount`. Never read-then-write without protection.

---

## Checklist

- [ ] I named every shared mutable state in my design.
- [ ] Each external mutation API takes an idempotency key.
- [ ] Each row mutation either has a `version` (optimistic) or runs in a `FOR UPDATE` (pessimistic).
- [ ] Each inventory decrement is a single atomic SQL statement with a guard.
- [ ] Each cross-service flow that needs atomicity uses Outbox or Saga.
- [ ] I named the failure mode (deadlock, retry storm, lost update) for each lock choice.
