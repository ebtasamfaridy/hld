# 11 · Food Delivery — Concurrency & Scaling

## Race conditions in this system

| # | Race | Impact |
| --- | --- | --- |
| 1 | Two requests place same order (network retry) | Double charge, double delivery |
| 2 | Customer cancels while restaurant accepts | Inconsistent state |
| 3 | Two dispatchers offer same driver | Driver double-booked |
| 4 | Driver accepts two offers in parallel | Double commitment |
| 5 | Inventory: last item bought twice | Overselling |
| 6 | Surge factor read while expiring | Inconsistent price across cart and order |
| 7 | Outbox poller crashes after Kafka publish, before stamp | Duplicate event publication |

We handle each.

---

## 1. Idempotent order placement

```java
public Order placeOrder(PlaceOrderCommand cmd) {
  // 1. fast path: existing idempotency record
  Optional<Order> existing = orderRepo.findByIdempotencyKey(cmd.idempotencyKey());
  if (existing.isPresent()) return existing.get();

  // 2. validate, reserve inventory, charge, persist
  PriceBreakdown price = pricing.compute(toCart(cmd));
  inventory.reserve(cmd.items());
  Payment p = payment.charge(price.total(), cmd.idempotencyKey() + ":pay");
  Order o = Order.builder()
              .id(UUID.randomUUID())
              .customer(cmd.customerId())
              .restaurant(cmd.restaurantId())
              .priceBreakdown(price)
              .items(cmd.items())
              .idempotencyKey(cmd.idempotencyKey())
              .status(OrderStatus.PLACED)
              .build();
  try {
    return orderRepo.save(o);
  } catch (DuplicateKeyException e) {
    // someone else won the race; reload and return
    return orderRepo.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow();
  }
}
```

The DB UNIQUE constraint on `idempotency_key` prevents duplicates absolutely. Retry logic handles the lost race.

---

## 2. Optimistic locking on order transitions

Every state-changing update goes through:

```sql
UPDATE orders
SET    status = ?, version = version + 1, updated_at = now()
WHERE  id = ? AND version = ?;
```

The `WHERE version = ?` makes the update **conditional** on the read version. If it fails (0 rows), we re-read and retry up to 3 times.

```java
for (int attempt = 0; attempt < 3; attempt++) {
  Order o = orderRepo.findById(id).orElseThrow();
  o.confirm();
  if (orderRepo.compareAndSwapStatus(o)) return o;     // returns false on version mismatch
}
throw new OptimisticLockException();
```

This handles **races 2 and 3** without locking long-running transactions.

---

## 3. Dispatcher: `FOR UPDATE SKIP LOCKED`

When dispatcher wants to assign an order:

```sql
SELECT id FROM drivers
WHERE city = ? AND status = 'IDLE'
ORDER BY ST_Distance(pickup_point, location) ASC
LIMIT 5
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` prevents two parallel dispatchers from selecting the **same** driver. Dispatcher 1 locks driver A; dispatcher 2 silently skips A and gets the next candidate.

After selection:

```sql
UPDATE drivers SET status = 'OFFER_PENDING', version = version + 1
WHERE id = ? AND status = 'IDLE' AND version = ?;
```

---

## 4. Driver double-accept guard

When the driver app sends `accept`:

```sql
UPDATE drivers SET status = 'BUSY', version = version + 1
WHERE id = ? AND status = 'OFFER_PENDING' AND version = ?;
```

If the driver already accepted another offer in between (and is now BUSY), this update fails. We respond `409 ALREADY_ASSIGNED`.

We also check the assignment:

```sql
UPDATE delivery_assignments SET status = 'ACCEPTED', responded_at = now()
WHERE id = ? AND status = 'OFFERED';
```

Only the first call succeeds.

---

## 5. Inventory atomic decrement

```sql
UPDATE menu_item_inventory
SET stock_count = stock_count - ?, updated_at = now()
WHERE menu_item_id = ? AND stock_count >= ?;
```

If 0 rows, throw `ITEM_OUT_OF_STOCK`. No lock needed.

For high-velocity items, **Redis** is the source-of-truth with a Lua script for atomicity, periodically reconciled to Postgres.

```lua
-- KEYS[1] = "stock:menu:m_9", ARGV[1] = quantity
local v = tonumber(redis.call('GET', KEYS[1]))
if v == nil or v < tonumber(ARGV[1]) then return -1 end
return redis.call('DECRBY', KEYS[1], ARGV[1])
```

Lua scripts run **atomically** in Redis. No race possible.

---

## 6. Surge factor caching

Surge is a **read-mostly** value updated every 1–5 minutes per geohash cell.

- Stored in Redis with key `surge:<geohash7>`.
- Read with TTL of 5 s (in-process micro-cache to absorb burst reads).
- When the customer **adds first item to cart**, we **lock the surge factor** into the cart for 10 minutes.

```java
Cart cart = cartRepo.findOrCreate(userId);
if (cart.surgeLockExpiresAt().isBefore(Instant.now())) {
  cart.lockSurge(surgeProvider.read(pickupGeo), Instant.now().plus(10, MINUTES));
}
```

This prevents the customer from seeing one price and being charged another.

---

## 7. Outbox + idempotent consumers

Outbox guarantees **at-least-once** publication. To get **exactly-once side effects**, consumers must be **idempotent**.

```java
public void onOrderPlaced(OrderPlaced e) {
  if (notificationLog.alreadySent(e.orderId(), Channel.SMS_ORDER_PLACED)) return;
  smsClient.send(...);
  notificationLog.record(e.orderId(), Channel.SMS_ORDER_PLACED);
}
```

Each consumer keeps a small idempotency table. The cost is one extra row per event per consumer.

---

## Geospatial indexing

### Why

Two hot queries:
- "find drivers within 3 km of point P"  ← runs ~500 RPS at peak.
- "find restaurants within 5 km of point P" ← runs ~10 K RPS.

We cannot scan all drivers/restaurants on every query.

### Index choices

| Index | How it works | Use |
| --- | --- | --- |
| **Geohash** | Encodes (lat,lng) as a string; same prefix = nearby | Simple, language-agnostic |
| **S2 cell ID** | Hierarchical spherical cells; balanced; Google's choice | High-precision proximity, geofences |
| **Quadtree** | Recursive subdivision of plane | Custom systems |
| **PostGIS GIST** | R-tree style spatial index | Postgres native |
| **Redis Geo** | Geohash + sorted set | Sub-ms `GEOSEARCH` |

For drivers (high write rate): **Redis Geo**.
For restaurants (low write rate, complex query): **PostGIS GIST** or pre-computed neighbor lists.

### Geohash mental model

A geohash like `tdr1y3` represents a rectangle on Earth. Longer = smaller. Two points with the same prefix are nearby.

```
tdr1y3 ≈ ~600 m × ~600 m  (length 6)
tdr1y3z ≈ ~150 m × 150 m  (length 7)
```

To find drivers near a point at length 7:
1. Compute geohash of point at length 6 (~600 m grid).
2. Look at this cell **and its 8 neighbors** (to handle edge cases).
3. Filter results by exact distance.

Redis `GEOSEARCH` does this for you: `GEOSEARCH driver:locations FROMLONLAT 77.59 12.97 BYRADIUS 3 km`.

### S2 vs Geohash

| Property | Geohash | S2 |
| --- | --- | --- |
| Equal cells at equator? | yes | yes |
| Equal cells at poles? | no (squashed) | yes |
| Indexable as integer | needs encoding | native uint64 |
| Library support | broad | Google libraries |

For a global service like Uber, S2 wins. For a single-country food delivery, geohash is fine and simpler.

---

## Order batching (V2 deep dive)

The dispatcher can pick **two orders to one driver** when:

- Both restaurants are within ~500 m of each other.
- Both delivery destinations are within ~1 km of each other.
- Combined route is < 1.5× longer than separate trips.

### Algorithm sketch

```
For each unassigned order O:
  if any active driver D is in BUSY with assignment A:
    if A.delivery is near O.pickup AND
       O.delivery is near A.delivery:
       offer batch to D
       continue

  // else normal single-order dispatch
  candidates = finder.findCandidates(O.pickup, 3km, 10)
  best = max by ScoringStrategy.score
  offer to best
```

Driver state has to expand: `BUSY` becomes `BUSY_SINGLE` and `BUSY_BATCH`. Or the assignment carries a list of orders.

We cover the data model evolution in `13_extensions_and_tradeoffs.md`.

---

## Scaling plan

### Vertical → horizontal

- Stateless services scale horizontally behind a load balancer.
- Sticky sessions only for WebSocket tracking.
- DB scales vertically first, then read replicas, then partitioning.

### When to shard the orders DB

When write rate > 5 K/sec sustained or storage > 5 TB.
- Shard key: `customer_id` (most reads are per-customer).
- Cross-shard queries (admin) handled via async ETL → analytics warehouse.

### When to introduce Cassandra/ScyllaDB

For driver location archive (huge write volume, single-key access):
- Key: `(driver_id, ts)`.
- TTL: 30 days.
- Queries: "give me locations of driver X in time range Y."

Don't use it for orders. ACID matters there.

### When to introduce Elasticsearch

For restaurant search by name/cuisine/free-text. Not in V1; add when search becomes slow on Postgres (typically > 50 K restaurants in a city or fuzzy matching).

---

## Caching strategy

| Layer | What | TTL |
| --- | --- | --- |
| CDN | menu images, static restaurant covers | 7 d |
| Redis | menu JSON, restaurant metadata | 60 s |
| Redis | inventory hot stock | live (Lua) |
| Redis | surge factor per geohash | 60 s |
| In-process | featureflags | 30 s |
| In-process | settings | 5 m |

We use **cache-aside** for menus: read → cache miss → DB → populate. On write (menu update), publish a `MenuUpdated` event; consumers invalidate the cache.

---

## Failure modes & their tests

| Failure | Recovery | Test |
| --- | --- | --- |
| Postgres primary failover | Connection retry; idempotent commands replay | Chaos test killing primary |
| Redis flush | Cache rebuilds from DB; brief slowdown | Restart Redis under load |
| Kafka outage | Outbox holds events; resumes on restore | Pause Kafka brokers |
| Payment gateway timeout | Cancel order + release inventory; surface `PAYMENT_TIMEOUT` | Stub gateway delays |
| Driver app loses connectivity | Auto-offline after 60 s no ping; reassignment | Simulate driver no-ping |
| Restaurant POS offline | Auto-confirm if SLA breached, or auto-reject | Pause POS responses |

Have a chaos schedule that exercises each at least monthly.

---

## Output

We have addressed every race condition with a specific mechanism:

- Idempotency at the API boundary.
- Optimistic locking on every state transition.
- DB CAS for inventory.
- `FOR UPDATE SKIP LOCKED` for dispatcher.
- Outbox for events.
- Idempotent consumers for at-least-once delivery.

This is the level of detail expected at staff.
