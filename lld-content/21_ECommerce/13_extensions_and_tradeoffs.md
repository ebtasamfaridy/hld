# 13 · E-Commerce — Extensions and Tradeoffs

## V2 features

| Feature | What it adds | Cost / risk |
| --- | --- | --- |
| **Subscriptions / Subscribe & Save** | Recurring orders (every 30 d, etc.) | Scheduler + savings calc; MIT for each cycle; pause/skip UX |
| **Prime / loyalty membership** | Free shipping + faster delivery + exclusive deals | Membership ledger; "prime-eligible" flag in offer search |
| **Reviews & ratings** | Buyer feedback on product + seller | Anti-fraud (fake reviews); ratings affect buybox |
| **Recommendations** | "Customers also bought" / "Inspired by your views" | ML pipeline + real-time feature store; cold-start handling |
| **Personalised search** | Re-rank by user history | Personalised cache keys; privacy guardrails |
| **Coupon / promo engine** | Code-based + auto-applied discounts | Specification pattern; abuse detection; eligibility rules |
| **Wishlists** | Save items for later, share lists | Cross-cart persistence; not money-bearing |
| **Q&A on products** | User asks, seller / community answers | Moderation; abuse + spam filtering |
| **Live shopping** | Streamed seller-led shopping events | Live capacity, real-time inventory updates, peak surges |
| **Group buying** | "Buy 3 with friends, get 30% off" | New aggregate (GroupOrder) with TTL + minimum-fill |
| **B2B / GST invoicing** | Org buyers, tax invoices | GSTIN capture + GST-compliant invoices; org-level credit |
| **Multi-currency / international** | Ship across borders | Currency conversion, customs, FX hedging, regional law |
| **Fraud detection** | Real-time transaction risk | Risk score in saga; soft-decline + manual review |
| **Damaged-in-transit auto-refund** | ML on photos at delivery | Proactive ops; same Returns flow underneath |
| **Buyer-protection / A-Z guarantee** | Platform-funded refunds when seller fails | Escrow for high-risk SKUs; arbitration workflow |

---

## Architectural tradeoffs

### Reserve-then-pay vs Pay-then-reserve

**Reserve-then-pay (chosen V1)**
- Pro: User never gets "paid but nothing in stock".
- Pro: Inventory consistency is the *only* hard rule on the buy plane.
- Con: Holds inventory for the duration of authorize (~ a few seconds); a malicious bot could spam place-order to deny inventory briefly.
- Con: Authorize failure means we have to compensate inventory.

**Pay-first**
- Pro: No inventory hostage during checkout.
- Con: Synchronous gateway latency on the buy hot path; refund flow on rare conflicts is bad UX ("we charged you but lost the last unit; we're refunding").
- Con: Increases support burden — "where's my refund?"

We chose reserve-first. Mitigations: per-user place-order rate limit, captcha on suspicious bursts, short saga timeout, reconciliation worker for orphan auths.

### Hard reserve at place-order vs Soft hold in cart

**Hard reserve at place-order (chosen)**
- Pro: Cart is honest about "this might still be available."
- Pro: No DoS on hot SKUs by users hoarding in cart.
- Con: User is surprised by 409 OUT_OF_STOCK only at checkout.

**Soft hold in cart (15-min TTL)**
- Pro: Smoother checkout — what's in cart is yours.
- Con: Hostile to popular sales — first-N visitors lock everything by adding to cart with no intent.
- Con: Adds a TTL sweep + complex "your cart is expiring" UX.

We chose place-order hard reserve. The race is brief (sub-second) and bounded.

### Per-seller inventory rows vs aggregate counters

**Per-seller (chosen)**
- Pro: Marketplace truth — different sellers have different stock.
- Pro: Buybox needs offer-level granularity anyway.
- Con: A single product spans many rows; cross-seller "is anything available" needs a small fan-out.

**Aggregate per SKU**
- Pro: Single counter per SKU.
- Con: Loses the marketplace dimension entirely; you'd have to re-introduce it elsewhere.

This isn't really a choice for a marketplace — per-seller is required. Aggregating would only suit a single-seller storefront (Shopify-style).

### Multi-shipment vs single-shipment per order

**Multi-shipment (chosen)**
- Pro: Sellers ship independently; order lifecycle is realistic.
- Pro: Per-shipment capture matches the cash flow.
- Con: Order status is a derived projection; harder to reason about than a single status field.

**Single-shipment**
- Pro: Simpler order model.
- Con: Wrong for marketplace — forces us to wait on slowest seller before shipping anything; or to model each as a sub-order which is just multi-shipment with extra steps.

Multi-shipment is the right model for a marketplace.

### Synchronous saga vs Workflow engine (Temporal/Cadence)

**Synchronous in-process saga (chosen V1)**
- Pro: Simple debugging, transparent code path.
- Pro: Fast on the happy path.
- Con: Compensations are manual; saga state is ephemeral (lost on crash unless checkpointed).
- Con: Doesn't handle long-running waits gracefully.

**Workflow engine**
- Pro: Built-in retries, compensations, durable state.
- Pro: Visualises in-flight workflows for ops.
- Con: New infrastructure; learning curve; latency overhead.

For a 6-step saga that completes in ≤ 1 s, in-process is fine. We graduate to Temporal when:
- Saga grows past 5 steps with human-in-the-loop pauses (returns flow already qualifies).
- Compensation logic becomes hard to maintain inline.
- Operations need ad-hoc replay / inspection of in-flight workflows.

V2: returns flow moves to a workflow engine first (it has multi-day waits + multiple actors). Place-order stays in-process.

### Browse plane consistency

The browse plane is intentionally **eventually consistent** with inventory truth. ES is updated via CDC; lag ≤ 30 s.

| Aspect | Browse plane | Buy plane |
| --- | --- | --- |
| Consistency | Eventual (≤ 30 s) | Strong |
| Latency | < 200 ms | < 800 ms |
| QPS | 100K peak | 5K peak |
| Data store | ES + Redis + CDN | Postgres |
| Failure impact | Search degrades | Cannot place order |

If the user sees "in stock" in search but place-order fails with OUT_OF_STOCK, they retry. We surface alternatives automatically. This is the right tradeoff — making search strongly consistent would kill latency without UX gain.

### BuyBox in Redis vs computed on every read

**Cache (chosen)**
- Pro: Search latency stays low.
- Con: Stale ≤ 5 s after offer changes.

**Compute on read**
- Pro: Always exact.
- Con: Expensive at search QPS — ~10× the work per query.

BuyBox staleness is a UX issue, not a correctness issue — place-order re-validates the offer + price. We accept the 5-s eyebrow-raise.

### Returns: synchronous vs async

**Async (chosen)**
- Pro: Buyer's "request return" is fast; warehouse + courier work async.
- Con: Refund happens days after request, requires saved-method MIT.

**Synchronous on request**
- Pro: Buyer sees money back immediately.
- Con: Risk of buyer scams (request return, get refund, never ship the item back).

Async returns + warehouse inspection is the only sensible choice for a marketplace; the "instant refund" experience can be selectively offered for low-value categories where fraud risk is acceptable.

### Single-currency vs multi-currency

V1 is single-currency. Adding multi-currency requires:
- Offer prices stored in seller's currency, displayed in buyer's.
- FX-rate snapshot on the order at place-order time.
- Refunds in original currency (not buyer's at refund time).
- Hedging policy for FX exposure between order and capture/refund.

We delay this to V2 because it's a substantial cross-cutting concern.

---

## What we'd do differently for...

### Single-retailer (e.g., warehouse-only Amazon basics, Croma online)

- One implicit "seller" for all inventory.
- BuyBox collapses to "the offer" — no scoring needed.
- Per-seller inventory rows become per-warehouse rows (similar shape).
- Simpler payouts (none — internal accounting).

### Etsy / handmade (each seller is an independent shop)

- Sellers fulfill from their own location, not platform warehouses.
- Shipping rates are per-seller, not platform-wide.
- Returns flow goes seller-to-seller, not via platform warehouses.
- Buybox doesn't exist — each shop's offer is the only one for that item.

The data model survives — only the routing changes.

### Flipkart (similar to Amazon but India-specific)

- COD (cash on delivery) is a major V1 payment method — a different two-phase ("AUTH-on-delivery" → CAPTURE-after-callback").
- GSTIN handling for B2B.
- Aggressive Big-Billion-Day capacity planning + pre-warming.

### Instacart / quick-commerce (1-hour delivery)

- Inventory is per-store (a darkstore), not per-seller.
- Time becomes a first-class element: a SKU is "available right now in this darkstore".
- Substitutions: when the picker can't find an item, they propose a swap. This is a new sub-state and a buyer-confirm flow.
- Same ListingOffer model with different weights in BuyBox (proximity dominates).

### B2B (Alibaba)

- Quote → negotiate → order rather than direct buy.
- Bulk pricing tiers. Custom SKU configurations.
- 30/60/90-day credit terms instead of synchronous payment.
- The "Order" aggregate gains a `quote_id` and a status arc for negotiation.

---

## Tradeoffs in plane separation revisited

The plane separation is what enables sale-day survival. Browse plane (read-heavy, cacheable, eventually consistent) scales horizontally on cheap commodity infra. Buy plane (write-heavy, strongly consistent, idempotent) is the small-but-precious core.

If a single team decided "we'll use the same DB for catalog and orders to keep things simple", the first sale-day would take down the whole site — search + cart + place-order all competing for the same connection pool.

The same lesson applies to BookMyShow (browse vs reserve), Car Rental (search vs place-reservation), Streaming (browse vs play). It's the canonical pattern of "consistency where you need it, scale everywhere else."

---

## Output

```
V2:           subscriptions, prime, recommendations, reviews, group-buy, B2B, multi-currency
Tradeoffs:    reserve-first vs pay-first, hard reserve, multi-shipment, sync saga vs workflow engine
Plane:        browse (eventual) vs buy (strong) — by design
BuyBox:       cached, eventually consistent ≤ 5 s; place-order is the truth
Variants:     single-retailer (no buybox), Etsy (no platform fulfilment), Flipkart (COD), Instacart (per-darkstore)
Underneath:   the same atomic-CAS-on-inventory mutex pattern as BookMyShow + Car Rental
```
