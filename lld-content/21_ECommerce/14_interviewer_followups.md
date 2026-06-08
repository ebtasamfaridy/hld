# 14 · E-Commerce — Interviewer Follow-ups

The questions a Staff+ interviewer is likely to ask. Each comes with a one-paragraph answer outline.

---

### 1. "Walk me through what happens, end-to-end, when a user clicks Place Order."

Idempotency lookup — return cached if duplicate. Hydrate cart, recompute prices, surface stale lines as 409 PRICE_CHANGED if any. Group cart lines by `seller_id` to materialise shipments. Atomically decrement inventory per `(seller_id, sku_id)` via `UPDATE … WHERE available >= qty`; if any line returns 0 affected rows, rollback all priors and return 409 OUT_OF_STOCK. Authorize total on the gateway with deterministic idempotency key `order_id`. Persist Order + OrderItems + Shipments + Payment + outbox(OrderPlaced) in one TXN. Return 201. Outbox publisher drains to Kafka — notification, search index updates, seller dashboards consume async. Total p99 budget ~800 ms; gateway dominates.

### 2. "Two buyers tap Place Order on the last unit at the same millisecond. How do you guarantee no oversell?"

The PK `(seller_id, sku_id)` plus the predicate `available >= qty` is the natural mutex. Both transactions try to UPDATE the row; Postgres serialises on the row-level lock; one wins, the other affects 0 rows. The losing app rolls back its entire reservation TXN, doesn't authorize payment, and returns 409 OUT_OF_STOCK with the failing line's `(offer_id, available)`. The system is correct independent of how many concurrent attempts hit the same offer. This is the same mutex pattern as BookMyShow's seat hold and Car Rental's slot insert.

### 3. "Why a three-layer catalog (Product → SKU → ListingOffer)?"

Each layer answers a distinct question. Product is the *logical* entry the buyer searches for ("iPhone 15"); it owns title, photos, reviews, ratings — the things shared across variants. SKU is the *variant* the buyer picks ("256 GB Black"); it owns gtin, weight, dimensions — the things shared across sellers. ListingOffer is the *per-seller* offer, the inventory-bearing thing — price, stock, ships-in-days. Collapsing Product + SKU loses variant search. Collapsing SKU + Offer loses the multi-seller dimension. Get this hierarchy wrong and your inventory math is wrong forever — you'll either oversell across sellers or fail to expose the buybox.

### 4. "What if a hot SKU on Big Billion Day attracts thousands of place-order attempts per second?"

Single-row CAS bottlenecks at ~200 QPS realistic in Postgres. Mitigations in order: (a) **bucket-split** the row — split `(seller, sku)` into N buckets; each buyer hashes to one; total available is sum-across-buckets; CAS contention spreads across N rows. (b) **Front-of-queue** in Redis Streams — per-SKU pipeline, drained by a single worker, converting random concurrency into a serialised pipeline. (c) **Rate-limit at the gateway** — only N attempts/sec/SKU; surplus gets a "try again" response. (d) **Pre-warm the buybox cache + ES** so reads don't fan out to Postgres. We start naive, profile, then bucket-split only on observed hot SKUs. Same playbook as a single hot showtime in BookMyShow.

### 5. "User clicks Place Order, network drops, they retry. How do you not double-charge?"

Two layers of idempotency. (a) Our API: client supplies `Idempotency-Key`, stored as `UNIQUE (user_id, idempotency_key)` on orders. The duplicate INSERT no-ops; we look up the existing order by key and return it. The duplicate request never reaches the inventory decrement step because the idempotency lookup returns early. (b) Gateway: we pass `order_id` as the gateway's idempotency key; the gateway dedupes too. Even if our app crashed between authorize and orders-TXN-commit, the next retry's gateway call dedupes and the orphan-auth reconciliation worker eventually voids it.

### 6. "Authorize succeeds but the orders-TXN fails. What's the user's experience and how do you recover?"

The user sees an error / retry on their app. The reconciliation worker (every 5 min) sweeps `payment_authorizations` older than 30 min that have no corresponding `orders` row, and calls `gateway.voidAuth(authId, idemKey=authId)`. The gateway dedupes if it was already voided. Inventory units that were decremented are released (we keep an `inventory_holds` ledger keyed by `order_id` so we know what to release). The user's card hold drops within minutes; from their perspective they see an error, retry, and either succeed cleanly or see a stable error (e.g., real OUT_OF_STOCK).

### 7. "How do shipments get created and why isn't 'partial place-order' allowed?"

At place-order, we group the cart's lines by `seller_id` (or `seller_id × warehouse` for prime fulfilment) — each group becomes one Shipment, owning a subset of OrderItems. Partial place-order would mean: "5 lines from 5 sellers, sellerX out of stock → place 4, fail 1." We choose the cleaner UX of failing the whole order with 409 OUT_OF_STOCK; the buyer can remove the failing line and retry. Splitting an order into a partial success surprises buyers and creates a refund flow that's hard to message. The cost — buyer retries — is negligible compared to support burden.

### 8. "Capture happens per shipment. What if the auth window expires before the slowest shipment dispatches?"

Two phases. (a) Within auth window (typically 7 days for cards): each dispatch issues `gateway.capture(authId, amount, idemKey=shipmentId)`; sum of captures ≤ authorized. (b) Past auth window: fall back to MIT — `gateway.mit(savedMethodToken, amount, idemKey=shipmentId)` against the saved payment method, with explicit consent recorded at order time. If MIT also fails (card invalid, insufficient funds), the shipment moves to ON_HOLD and ops dunning kicks in. The legal angle: at place-order we record consent for "merchant-initiated transactions for delayed shipments and damage claims" — the standard model for marketplace fulfilment.

### 9. "How do you split data across services / shards?"

Catalog by product_id (read-heavy, cacheable). Listing offers by sku_id (co-locate with inventory + buybox). Inventory units by sku_id (consistent hash; spreads write load). Carts by user_id (Redis primary). Orders by user_id (so "my orders" is a single shard read), partitioned monthly. Order items + shipments + payments co-located with orders by `order_id` -> user_id. Returns by shipment_id. CDC topic per major table.

### 10. "Returns can result in a refund days/weeks after capture. How do you handle that?"

Returns are a separate aggregate. Buyer requests within the return window (default 7 days, per-category override). RetService creates a Return row in REQUESTED status, schedules courier pickup. Pickup → warehouse → inspection. On `passed=true`: `payment.refundForReturn(captureId, amount, idemKey=return_id)` — gateway-side refund against the original capture (most gateways support refunds up to N months post-capture). If outside the gateway's refund window, fall back to a credit note or MIT credit. Inventory: `restock=true` increments the unit; `restock=false` writes it off. Return is REFUNDED; user sees money back in 5-7 days.

### 11. "BuyBox — many sellers list the same SKU. How is the winner chosen and how often does it change?"

A `BuyBoxStrategy` scores each candidate offer on `(price, ships-in-days, seller-rating, prime, in-stock)`. The default is a weighted score; we have variants like `LowestPriceBuyBox` and `PrimeFirstBuyBox`. Recompute is event-driven: an OfferUpdated or InventoryUpdated event triggers a per-SKU recompute, debounced to one per second. Result is cached in Redis with 5-second soft TTL. So buybox is eventually consistent within ~5 s of the underlying change. Place-order re-validates the offer (price + status), so buybox staleness can't cause incorrect billing.

### 12. "Walk through cancelling a shipment that's already DISPATCHED but not OUT_FOR_DELIVERY."

Status-guarded UPDATE first: `UPDATE shipments SET status='CANCELLED' WHERE id=? AND status IN ('CREATED','PACKED','DISPATCHED')`. If the row was already captured (`capture_id IS NOT NULL`), refund route is `payment.refund(captureId, amount, idemKey=cancelId)` — gateway refund. If not yet captured (race condition), route is `payment.voidPartialAuth(authId, amount, idemKey=cancelId)`. Inventory increments back. Carrier gets an RTO request via `carrierAdapter.initiateRTO(awb)`. The order's status projects to PARTIALLY_REFUNDED (or CANCELLED if this was the only shipment).

### 13. "Cancel race vs dispatch — buyer cancels at 10:00:00.000, seller dispatches at 10:00:00.001. Both see PACKED. Who wins?"

Status-guarded UPDATEs serialise the conflict: cancel does `UPDATE … WHERE status='PACKED'`; dispatch does the same predicate to transition to DISPATCHED. Postgres locks the row; one TXN commits, the other sees zero affected rows and reports the appropriate "already X" error. The losing path unwinds — cancel sees `0 rows affected, status='DISPATCHED'`, returns 409 SHIPMENT_ALREADY_DISPATCHED, instructs buyer to use returns flow. Dispatch sees `0 rows affected, status='CANCELLED'`, reverses any AWB allocation made before the DB write. No money moves twice; no inventory drifts.

### 14. "Cart staleness — buyer added at ₹79,900 yesterday; today the price is ₹74,999 (or ₹85,000). What happens at checkout?"

At place-order, we recompute each line's current price against `priceAtAdd`. If any differ, we 409 PRICE_CHANGED with both old and new prices. The buyer sees a "price changed" UI and must explicitly accept before retrying. We never silently charge the new price; we never silently honour the stale price. Surprising the buyer in either direction is worse than the friction of an explicit accept. The accept event is logged for audit (settles "did the buyer know the price?" disputes).

### 15. "Big-Billion-style sale starts at midnight. Search and PDP traffic 100×. How does the system survive?"

Plane separation — the browse plane (search, PDP, buybox) scales horizontally and serves from CDN + Redis + ES read replicas. The buy plane (place-order, capture) is small and write-heavy; we pre-warm caches and have head-room budgeted for ~5K place-order QPS. Specific tactics: (a) pre-warm BuyBox + offer cache for all on-sale SKUs. (b) Relax ES refresh interval to 5 s during peak (eventual consistency lag tolerable). (c) Bucket-split hot SKUs proactively. (d) Per-user place-order rate limit on the gateway. (e) Captcha on suspicious bursts. (f) Async pricing recompute on cart with debounce. The goal: even if 1% of search traffic places orders, the buy plane handles it.

### 16. "How do refunds against an expired auth get charged back?"

If the cancel happens within the gateway's refund window against the original capture: `gateway.refund(captureId, amount)` works directly. If past the refund window (rare; some gateways have ~180-day windows): fall back to a credit note in our ledger and a manual cheque or MIT credit run by ops. Best practice: enforce that returns are within 7 days of delivery and captures happen at dispatch (within 7 days of order); refunds therefore always within ~14 days of capture, well within gateway windows.

### 17. "A seller is suspended mid-order — what happens to in-flight shipments?"

Suspending a seller transitions all their offers to SUSPENDED (hidden from search + buybox). Existing shipments aren't auto-cancelled — the buyer might still receive their goods. Ops reviews each in-flight shipment: (a) DISPATCHED + tracking live → let it deliver, hold seller's payout pending review; (b) PACKED but not yet shipped → ops decision: ship via platform, refund and cancel, or wait. Pending payouts are frozen. Returns from this seller continue to process; refunds come from platform reserve, with claim against seller's frozen payout.

### 18. "How do seller payouts work?"

Each shipment captures money at dispatch into a platform-held escrow. After delivery + return-window-expiry (delivery + 7 days, default), the shipment is "settled" — net of platform fees, the amount moves to the seller's payout queue. Payout runs daily (or on-demand for large sellers); idempotent on `(seller_id, payout_run_id)`. Returns claw back from the next payout run. Pending payouts are visible in the seller dashboard. If a seller has more refunds than incoming captures, payouts go negative — we hold against future captures, or invoice the seller.

### 19. "Tell me about an edge case that's hard to test."

**Reservation-but-no-order**: inventory decrement succeeds, payment authorize succeeds, persist orders TXN fails (rare DB hiccup or app crash). Inventory shows "reserved" but the user has no order. Hard to test because all happy-path tests succeed; this is exception-during-step-6. Mitigation: chaos test that injects DB errors mid-saga. Reconciliation worker picks up orphan auths every 5 min and voids them. We monitor `orphan_auths_voided` as a key reliability metric — > N per day means something's wrong upstream.

### 20. "What's the trickiest correctness bug you'd anticipate, and how do you debug it?"

**Phantom oversell** during a CDC-driven inventory drift. Sellers sometimes update inventory via webhook from their ERP — say they push `available=5` while we have 3 reserved + 2 free. Naive handling overwrites our state and apparent oversells happen on the next race. The right model is: external updates are *deltas*, not absolutes. Adjust API takes `delta=+N`, idempotent on `(seller_id, idempotency_key)`. If the seller's ERP only knows absolutes, we compute the delta server-side based on our last-synced absolute. Debugging: aggressive logging of every adjust call, nightly recon comparing our `available + reserved` to seller-reported total, ops dashboard for divergent SKUs. The fix once observed: rate-limit external adjusts + force them through the delta API.

---

## Closing remarks

If you internalise five things from this system, you can answer 90% of the e-commerce LLD interview:

1. **Three-layer catalog** — Product (logical), SKU (variant), ListingOffer (per-seller, inventory-bearing). Same shape as Library's Book → Copy or Car Rental's VehicleModel → Vehicle, with one extra layer for the marketplace dimension.
2. **Per-offer atomic CAS** — `UPDATE inventory_units SET available=available-qty WHERE … AND available >= qty`. The marketplace's no-oversell rule.
3. **Cart-to-order saga** — idempotency lookup, atomic inventory decrement (compensable), payment authorize (compensable), persist + outbox in one TXN. Reconciliation safety net for orphan auths.
4. **Multi-shipment + per-shipment capture** — one order, N shipments, N captures. Refund routing depends on capture state. Cancel windows shorten as the shipment progresses.
5. **Two-phase payment + MIT** — AUTH at order, CAPTURE at ship, MIT for late/delayed/damage charges, REFUND for cancels/returns. Belt-and-suspenders idempotency on every step.

Everything else (BuyBox, returns, seller payouts, fraud) is composition of these primitives.
