# 11 · E-Commerce — Concurrency and Scaling

## The six hot races

### 1. Last-unit oversell

**Scenario.** Aman and Maya both tap Place Order on the last iPhone unit from Acme Mobiles at the same millisecond.

**Naive bug.** Read `available`, see "1 in stock", decide it's free, decrement to 0 — both succeed. Two orders against one unit. Oversell.

**Fix.** Atomic CAS on the inventory row:

```sql
UPDATE inventory_units
   SET available = available - 1, version = version + 1
 WHERE seller_id = $s AND sku_id = $k AND available >= 1;
```

The PK `(seller_id, sku_id)` is the natural mutex. Postgres serialises; one wins, one returns 0 affected rows → 409 OUT_OF_STOCK. The system is correct independent of how many concurrent requests hit the same offer.

This is the same pattern as BookMyShow's seat hold (`UPDATE seats SET status='HELD' WHERE id=? AND status='AVAILABLE'`), Car Rental's slot insert (`INSERT … ON CONFLICT DO NOTHING`), and the canonical "decrement only if enough" idiom across the curriculum.

### 2. Idempotent place-order

**Scenario.** User clicks Place Order on a flaky network; client retries. Two requests with the same `Idempotency-Key` reach the server.

**Bug.** Two orders, two inventory decrements, two payment auths, double-blocked card.

**Fix.**
```sql
INSERT INTO orders (id, user_id, ..., idempotency_key)
VALUES (...)
ON CONFLICT (user_id, idempotency_key) DO NOTHING
RETURNING id;
```

If `RETURNING` is empty, look up the existing order by key and return it.

Belt-and-suspenders: gateway is also given a deterministic idempotency key (`order_id`), so even if our app crashed between TXN commit and 201-response, the gateway dedupes.

But what about the inventory decrement that runs *before* the orders TXN? Solution: the saga runs inventory.reserve() **after** the idempotency lookup. If the duplicate request's idempotency lookup hits the existing order, we skip the inventory decrement entirely.

### 3. Inventory drift between cart-add and place-order

**Scenario.** Aman adds a unit to cart at 10:00. The seller updates inventory to 0 at 10:30. Aman clicks Place Order at 10:31.

**Naive expectation.** Lookup inventory at place-order, see 0, fail.

**That's actually the correct behaviour.** Carts don't reserve inventory. Place-order is the consistency oracle. The user sees 409 OUT_OF_STOCK.

**Anti-pattern.** Some systems pre-reserve at "Buy Now" before payment auth. We don't — it creates a hostage problem (a single user can lock 100 units by starting 100 checkouts).

What we *do* offer: a brief soft-hold during the place-order saga itself (~ a few seconds, until inventory.reserve() completes).

### 4. Cart staleness — price changed

**Scenario.** Aman added an iPhone at ₹79,900. By checkout the price is ₹74,999 (sale started). Or worse — it's ₹85,000 (sale ended).

**Bug.** Charge `price_at_add` and the seller is paying out of pocket; or charge today's price and the buyer rage-tweets.

**Fix.** At place-order, recompute the cart against current prices:
```
foreach cart line:
   if currentPrice != priceAtAdd:
       collect into "stale" list
if stale.notEmpty:
   return 409 PRICE_CHANGED with both old and new prices
```

The buyer must explicitly accept the new price before retrying. We log the explicit accept event for audit.

### 5. Cancel race vs. dispatch

**Scenario.** Buyer taps Cancel on a PACKED shipment at 10:00:00.000. Seller's worker taps Dispatch at 10:00:00.001.

**Bug.** Both succeed; we capture money and refund money, leaving books inconsistent.

**Fix.** Status-guarded UPDATEs serialise:

```sql
-- Cancel path:
UPDATE shipments SET status='CANCELLED', version=version+1
WHERE id = $sid AND status IN ('CREATED','PACKED');

-- Dispatch path:
UPDATE shipments SET status='DISPATCHED', awb=$awb, version=version+1
WHERE id = $sid AND status='PACKED';
```

Whichever transaction commits first wins; the second sees zero affected rows and returns the "already X" error. The losing path then fully unwinds (cancel → no refund needed because the dispatch never captured; dispatch → reverse the AWB allocation if it preceded the DB write).

### 6. Reserved-but-orphaned auth

**Scenario.** Inventory.reserve succeeds. Payment.authorize succeeds. Then orderTXN fails (DB hiccup, app crash). Inventory is decremented; gateway holds money; no order in DB.

**Bug.** User's card is held with no order to show. Inventory is "missing" units.

**Fix.** Reconciliation worker, every 5 min:
```
SELECT auth records older than 30 min where no orders row exists with that order_id
FOR EACH:
  voidAuth(authId, idemKey=authId)  -- gateway dedupes
  release inventory units associated with that order_id (if any)
  emit OrphanAuthVoided
```

The reservation step writes a small `inventory_holds` ledger row keyed by `order_id` so we can compensate cleanly. (Alternative: keep state on the `inventory_units.reserved` counter, but that requires more bookkeeping.)

---

## Webhook idempotency

Gateway and carrier webhooks retry. Always.

```sql
INSERT INTO processed_events(event_id, source) VALUES ($eventId, 'gateway')
ON CONFLICT DO NOTHING
RETURNING event_id;
```

If RETURNING is empty → already processed → 200, no-op. Otherwise process inside the same TXN as the INSERT.

---

## Optimistic vs. pessimistic locking

| Resource | Strategy | Why |
| --- | --- | --- |
| InventoryUnit | Conditional UPDATE (`available >= qty`) | Atomic; lock-free at app level |
| Order | Insert-only after creation; status changes go through derived projection | Immutable in spirit |
| Shipment | Optimistic version on status updates; status-guarded WHERE | Multiple actors (buyer, seller, ops, system) |
| Payment | Strict allowed-transitions, serialised through PaymentService | One owner per payment lifetime |
| ListingOffer | Last-write-wins on price/SLA; CAS on stock | Edits are seller-driven, infrequent |
| BuyBox cache | Single-writer-per-SKU pattern via SKU-keyed worker | Avoid duplicate computation |
| Cart | Per-user lock (Redis WATCH) on multi-line atomic edits | Rare but matters when multiple devices edit |

We never `SELECT FOR UPDATE` on a hot row in the place-order path. The CAS approach is faster and scales horizontally.

---

## Scaling knobs

| Layer | Knob | Default | Effect |
| --- | --- | --- | --- |
| Search ES | replicas, refresh interval | 3, 1 s | Read fanout vs latency |
| CatalogDB | read replicas | 5 | PDP serving |
| InventoryDB | shards by sku_id | 32 | Spread CAS writes |
| OrderDB | shards by user_id, partitions by month | 64 / monthly | Spread place-order; cold rotation |
| BuyBox cache | Redis cluster nodes | 16 | Spread hot SKU keys |
| Cart Redis | cluster nodes | 32 | Per-user spread |
| Kafka partitions | per topic | 64–256 | Event throughput |
| Payment gateway pool | per gateway | 100 | Bulkhead between vendors |
| Carrier adapter pool | per carrier | 50 | Vendor isolation |
| Refund worker pool | autoscaled | RPS-driven | Backlog drain |

---

## Failure modes & mitigations

| Failure | Mitigation |
| --- | --- |
| InventoryDB shard down | Affected SKUs fail place-order; catalog soft-marks unavailable; other shards keep serving |
| Payment gateway down | Circuit breaker opens; place-order returns 503 with retry-after; queued captures retry on close |
| OrderDB primary down | Place-order fails fast; 30 s auto-failover to replica |
| Kafka down | Outbox accumulates; place-order unaffected (sync path doesn't depend on publish); BuyBox stops recomputing (stale data, not incorrect) |
| ES index drifted | Search results stale ≤ 30 s; place-order re-validates; OUT_OF_STOCK / PRICE_CHANGED surfaces correctly |
| Carrier API down | New AWB allocation queues; existing tracking webhooks unaffected |
| Seller webhook down | Inventory adjust requests buffer in queue; we serve our last-known counts |
| Refund gateway timeout | Retry with exponential backoff; alert ops after 24 h |
| Reconciliation worker dies | Idempotent; restart resumes via `published_at IS NULL` cursor |

---

## Hot-path latency budget — place order (target p99 < 800 ms)

| Step | Budget | Comment |
| --- | --- | --- |
| Idempotency lookup | 5 ms | Indexed point read |
| Cart hydrate | 10 ms | Redis HGETALL |
| Price recompute + validation | 15 ms | In-memory + cached offers |
| Inventory reserve (N rows) | 50 ms | Multi-row CAS in one TXN |
| Payment authorize | 500 ms | External gateway dominates |
| Persist order TXN | 50 ms | Multi-row INSERT |
| Build response | 5 ms | |
| **Total** | **~635 ms** | Headroom for retries |

If gateway p99 climbs > 1 s, we shift to **async authorize** — return PENDING immediately, complete auth in worker, push status via WebSocket. Trades simplicity for latency stability.

---

## Hot-key inventory (Big Billion Day)

A single hot SKU (e.g., the iPhone-on-sale offer) might attract 10K QPS. The single row becomes a serial bottleneck (~200 QPS realistic on Postgres for serialized writes).

Mitigations, in order:
1. **Bucket-split the row**: split `(seller, sku)` into N "shards" (`bucket=0..N-1`); a buyer hashes to one bucket. Total available = sum across buckets. Decrement on the hashed bucket. CAS contention now spreads across N rows.
2. **Front-of-queue**: a dedicated Redis Stream per hot SKU; the place-order worker drains in order. Effectively converts random concurrent decrements into a serialised pipeline.
3. **Rate limit / waiting room**: at the gateway layer, only N place-order attempts per second per SKU; surplus gets a "try again" response.
4. **Pre-allocation by user cohort**: first 1000 prime users see one bucket, next 1000 another, etc.

We start with naive single-row CAS, profile, and apply bucket-split only on observed hot SKUs.

---

## BuyBox storm (sale launch)

10M offer changes/day at sale-launch = brief storm of buybox recomputes. Mitigations:
- **Per-SKU debouncing**: events arriving within 1 s collapse to one recompute.
- **Lazy recompute on read**: if cache TTL expired and write event hasn't recomputed yet, the search service computes synchronously (rare path).
- **Pre-warm**: before the sale, recompute buybox for all on-sale SKUs and pin them in cache with a longer TTL.

---

## Refund surge (post-sale)

Big Billion's 7-day return window means a return spike on day-7+. Refund workers autoscale based on queue depth. Each refund is idempotent on `return_id`, so retries on transient gateway errors are safe. We monitor:
- Refund p99 latency.
- Refund retry rate.
- Refund-failed-after-N-retries (ops escalation).

---

## Reconciliation jobs

| Job | Cadence | Catches |
| --- | --- | --- |
| Orphan auth voider | 5 min | Auths with no order row |
| Inventory drift detector | 1 hour | Our count vs seller-reported count |
| Capture-on-dispatch SLA | 15 min | DISPATCHED shipments without capture |
| Refund SLA | 30 min | Refunds older than expected |
| Order completion projector | 5 min | Orders to mark COMPLETED based on shipments |
| Carrier "stuck" detector | 1 hour | OUT_FOR_DELIVERY > 48 hr |
| Buyer dunning | daily | Block users with N unpaid orders |

These are safety nets, not the primary path.

---

## Output

```
Hot races:    last-unit, idempotent place, drift, stale-price, cancel-vs-dispatch, orphan auth
Locking:      CAS on inventory_units; never hold DB locks across gateway calls
Scaling:      shard inventory by sku, orders by user, partition orders by month
Hot keys:     bucket-split popular SKUs at sale-day scale
Failure:      every component degrades independently; gateway + carrier + seller-webhook all circuit-broken
Latency:      gateway dominates place-order; everything else is single-digit ms
Webhooks:     dedupe by eventId; downstream is idempotent
Recon:        orphan auth, drift, refund SLA, completion projector — all idempotent loops
```
