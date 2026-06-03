# 13 · Food Delivery — Extensions and Tradeoffs

> A great design is not "complete." It is **evolvable**. This file shows how to grow this system without rewriting it.

---

## Tradeoffs we made (and the alternatives)

### 1. Postgres for orders vs Cassandra

| Choice | Postgres | Cassandra |
| --- | --- | --- |
| ACID | ✓ | ✗ (per row only) |
| Joins | ✓ | ✗ |
| Write throughput | ~10 K/s | 100 K/s |
| Operational maturity | extremely high | high |
| Sharding | manual | built-in |

We picked Postgres because:
- Orders need ACID across `orders` + `order_items` + `outbox`.
- Joins matter (admin views).
- 2.5 K writes/sec is well below Postgres limits.

If we exceed 10 K writes/sec, we **shard by `customer_id`** before reaching for Cassandra.

### 2. Sync vs async dispatch

| Choice | Sync (in request) | Async (event-driven) |
| --- | --- | --- |
| Latency | order ack waits for dispatch | order ack immediate |
| Coupling | tight | loose |
| Customer UX | "driver assigned" right away | "looking for a driver" |

We chose **async** — dispatch starts after `OrderConfirmed`. Order placement returns 201 in ~200 ms. The customer sees "looking for a driver" for 5–15 s.

Tradeoff: if dispatch fails entirely, the customer learns minutes later. We mitigate with a **5-minute reconciliation cron** that flags and reassigns stuck orders.

### 3. Optimistic vs pessimistic locking

We chose optimistic on every transition. Conflict rate < 1%. Pessimistic would block parallel updates and require careful lock ordering. The cost of optimistic is at most 3 retries, which is cheaper than holding row locks across an HTTP roundtrip.

### 4. Single-region vs multi-region

V1 is single-region. Multi-region would need:
- Geo-aware load balancing.
- Cross-region replication (and conflict resolution).
- Per-region driver pools.

We acknowledged this in NFR but deferred. Adding it later is **possible without rewriting** because services are stateless and DB has clean partition keys.

---

## V2 extensions

### A. Multi-restaurant cart

**Problem:** Customer wants Pizza + Pasta from two different restaurants in one delivery.

**Design impact:**
- Cart references **multiple** restaurant_ids.
- Order splits into N "sub-orders" — one per restaurant — but customer sees one order.
- Pricing aggregates across sub-orders.
- Dispatch matches **one driver** but with **multi-pickup** route.
- Order state machine complicates: each sub-order may transition independently.

**Schema delta:**

```sql
ALTER TABLE orders ADD COLUMN parent_order_id UUID REFERENCES orders(id);
-- Each sub-order points to a parent. Or:

CREATE TABLE order_groups (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  status VARCHAR(20),
  created_at TIMESTAMPTZ
);
ALTER TABLE orders ADD COLUMN order_group_id UUID REFERENCES order_groups(id);
```

The second approach is cleaner. Group is the customer-visible unit; orders remain restaurant-scoped.

**Why it's not in V1:** doubles the complexity of dispatch, payments, and state machines. Common interview anti-pattern: building V1 with V2's flexibility and being too slow.

### B. Scheduled orders

**Problem:** Customer schedules an order for 7 PM tomorrow.

**Design impact:**
- Order has a `scheduled_for` timestamp.
- Order sits in `SCHEDULED` state until ~30 min before scheduled time.
- A scheduler service moves `SCHEDULED → PLACED` at the right moment.
- Dispatch starts then.
- Restaurant's `prep_minutes` and driver ETA both account for the schedule.

**Concurrency:** ensure exactly one trigger fires per scheduled order. Use a row lock with `FOR UPDATE` on the scheduled_orders table when picked up by the worker.

### C. Subscription (Swiggy One)

**Problem:** Subscribed customers get free delivery, no surge.

**Design impact:** add a `Subscription` aggregate; the `PricingService` reads `Subscription.activeFor(customerId)` and applies different rules.

This is a **textbook OCP win**: existing pricing rules stay; new rules for subscribers added as `PricingRule`s. We only inject a different rule list when the customer is a subscriber.

### D. Order batching (1 driver, 2 orders)

Discussed in `11_concurrency_and_scaling.md`. Key design changes:

- Driver state expands: `BUSY → BUSY_BATCH | BUSY_SINGLE`.
- Assignment can carry multiple orderIds.
- Scoring strategy upgraded to `BatchAwareScoring`.
- Pickup state tracked per leg of the route.

The clean architecture means the existing `DispatchService` interface stays; only `ScoringStrategy` and `Assignment` evolve.

### E. Live tracking via WebSocket / SSE

V1 already plans for WebSocket-based tracking. V2 hardens it:
- Sticky sessions or use a fanout layer (e.g., Centrifugo, AWS API Gateway WS).
- Backpressure when clients are slow.
- Reconnect strategy from client.

### F. Restaurant search (Elasticsearch)

When restaurant DB grows beyond ~100 K and customers want fuzzy search, cuisine filters, and ranking by rating + delivery time, we add Elasticsearch.

Pattern: **CDC** (Debezium) consumes Postgres WAL and writes to ES. ES is the read store; Postgres remains the source of truth.

---

## Operational concerns

### Observability

- **Metrics**: order success rate, dispatch p95 latency, driver supply per geohash, surge factor distribution.
- **Logs**: every state transition with `request_id`, `actor_id`, `from_status → to_status`.
- **Traces**: OpenTelemetry across `OrderService → InventoryService → PaymentService → DispatchService`.
- **Alerts**: stuck orders > 20 min, dispatch p99 > 30 s, refund queue depth > 100.

### Feature flags

A FeatureFlag service drives rollouts:

```java
if (flags.isEnabled("batch-dispatch", customerId)) {
  scoring = new BatchAwareScoring();
}
```

This decouples deploy from release. Required for any real product.

### A/B testing

Pricing changes, dispatch algorithms, ranking — all benefit from A/B. Strategy pattern + flags make this straightforward.

### Multi-currency

If we expand internationally:
- All `Money` already carries a currency.
- `PricingService` uses currency from cart.
- An exchange-rate provider sets daily rates for cross-currency analytics.
- Each restaurant has a base currency.

The only place that breaks is the assumption "everything is INR" in summary queries. Easy fix.

---

## What this design will *not* support without major changes

- **Truly real-time pricing across thousands of variables** (à la Uber's Marketplace): would need a stream-processing engine (Flink) and a feature store. Out of scope for an LLD round.
- **In-app voice / video calls**: separate microservice with WebRTC.
- **Cross-platform retail (groceries, electronics)**: the `Restaurant` entity becomes a `Vendor`, the `MenuItem` becomes a `Product`. Many entity renames; expect a 2-month refactor.

State these explicitly to show you've thought about boundaries.

---

## Closing tradeoff summary

| Choice | We picked | Alternative | Why |
| --- | --- | --- | --- |
| Storage | Postgres | Cassandra | ACID + joins; numbers don't force NoSQL |
| Locking | Optimistic | Pessimistic | Low conflict rate |
| Dispatch | Async | Sync | Latency for placement |
| Region | Single | Multi | Premature for V1 |
| Cart | One restaurant | Multi-restaurant | Complexity vs MVP value |
| State pattern | Enum + map | Polymorphic state classes | Behavior diverges little |
| Event delivery | Outbox + Kafka | Direct publish | Durability under crash |
| Driver location | Redis Geo + Kafka | Postgres | RPS too high for RDBMS |
| Search | Postgres for V1 | Elasticsearch | Defer until needed |
| Idempotency | API key + UNIQUE | None | Mandatory for retries |

For each, you should be able to articulate the tradeoff in 30 seconds during an interview. That is the staff bar.
