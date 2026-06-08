# 01 · E-Commerce — Requirements

## Functional requirements

### Catalog

- **FR-1** — A `Product` is the **logical** entry (e.g. "Apple iPhone 15"). It owns title, description, brand, category, primary photos, specs, reviews.
- **FR-2** — A `SKU` is a specific variant of a product (`iPhone 15 / 256 GB / Black`). One product has many SKUs.
- **FR-3** — A `ListingOffer` represents a specific seller's offer of a SKU: price, available stock, fulfilment SLA, ships-in. One SKU has many offers (one per seller).
- **FR-4** — Each ListingOffer has its own inventory row. Stock decrements happen on the offer, not on the SKU.
- **FR-5** — One offer per `(seller_id, sku_id)` is the **buybox winner**, selected by a scorer (price + SLA + seller rating). Other offers are surfaced as alternates.

### Search

- **FR-6** — Buyer searches by free text + filters (category, price range, brand, rating, prime-eligible). Results are at the **Product** granularity (one card per product), not per offer.
- **FR-7** — Search supports facet aggregations (price buckets, brands, ratings).
- **FR-8** — Each result card displays the buybox offer's price + ships-in + seller name; "Other sellers" link expands to alternates.
- **FR-9** — Search results are **eventually consistent**; lag ≤ 30 s after catalog or inventory updates.

### Cart

- **FR-10** — Buyer adds `(offer_id, qty)` to cart. Cart line stores `price_at_add` for staleness detection.
- **FR-11** — Cart **does not reserve** any inventory. Stock is checked at checkout.
- **FR-12** — Carts have a soft TTL (30 days); items beyond may be auto-moved to "Saved for later".
- **FR-13** — At checkout, if any line's `price_at_add` differs from the current price, surface a warning before letting the user proceed.

### Checkout / Place Order

- **FR-14** — Place order accepts `(cartId, addressId, paymentMethodId, idempotencyKey)`.
- **FR-15** — The system validates address, runs a **place-order saga**: idempotency lookup → atomic inventory decrement per cart line → authorize total → persist order, items, shipments, payment in one TXN.
- **FR-16** — Idempotency: `UNIQUE(user_id, idempotency_key)` on orders. Repeat = same order, no duplicate.
- **FR-17** — Order is created in `CONFIRMED` status only after both inventory hold + payment auth succeed.
- **FR-18** — On any failure mid-saga, all earlier steps compensate (release inventory, void auth).

### Multi-shipment

- **FR-19** — An order is split into **one shipment per seller** (or per warehouse). Each shipment has its own status, AWB, and capture.
- **FR-20** — When a shipment is dispatched, its share of the authorized payment is captured. Authorization stays open until last shipment.
- **FR-21** — If a shipment is cancelled before dispatch, its inventory is released and its capture amount is voided (or refunded if already captured).
- **FR-22** — Order completes when all shipments are DELIVERED or CANCELLED.

### Cancellation

- **FR-23** — Buyer can cancel any shipment in `PACKED`, `DISPATCHED`, or earlier states. Once `OUT_FOR_DELIVERY`, cancellation requires return flow instead.
- **FR-24** — Partial order cancellation is allowed; uncancelled shipments proceed normally.
- **FR-25** — Cancellation releases that shipment's inventory atomically (`UPDATE inventory_units SET available = available + qty`) and refunds proportionally.

### Returns and refunds

- **FR-26** — Buyer can request a return on a delivered shipment within the return window (default 7 days, per-category override).
- **FR-27** — Returns transition: REQUESTED → APPROVED → PICKED_UP → INSPECTED → REFUNDED | REJECTED.
- **FR-28** — Refund processes asynchronously after inspection. Refund hits the original payment method via the gateway's refund API.
- **FR-29** — Approved returns increment inventory only when the item is restock-eligible (depends on category + condition).

### Payment

- **FR-30** — Place order **authorizes** the full order amount on the chosen payment method. No money moves yet.
- **FR-31** — Each shipment dispatch **captures** that shipment's amount. Sum of captures ≤ authorized.
- **FR-32** — If a capture occurs after the gateway's auth window expired, fall back to MIT (merchant-initiated transaction) using saved payment method.
- **FR-33** — Refunds are routed to: (a) the original auth (if voidable), (b) the original capture (if captured but within refund window), or (c) MIT credit (rare). The refund record links back to the source.

### Seller operations

- **FR-34** — Sellers list/edit/delete their offers. Each edit emits an event for search reindex + buybox recompute.
- **FR-35** — Sellers update inventory via dashboard or webhook from their ERP.
- **FR-36** — Sellers see incoming orders, mark shipments PACKED → DISPATCHED with AWB.
- **FR-37** — Sellers receive payouts on delivered + return-window-expired shipments, net of platform fees.

### Buybox

- **FR-38** — For each `(sku_id)`, a single offer is the **default buybox winner** at any time. It's selected by a `BuyBoxStrategy` (price, SLA, seller rating, prime-eligible).
- **FR-39** — Buybox recomputes on offer changes (price, stock zero, suspension), with eventual consistency ≤ 5 s.
- **FR-40** — UI exposes the alternates ("Other sellers from ₹74,999") so the user can override.

---

## Non-functional requirements

| ID | NFR | Target |
| --- | --- | --- |
| NFR-1 | Search p99 latency | < 200 ms |
| NFR-2 | Product detail page p99 | < 250 ms (cached) |
| NFR-3 | Place order p99 | < 800 ms (incl. gateway authorize) |
| NFR-4 | Inventory consistency | strong (no oversell, ever) |
| NFR-5 | Payment idempotency | 100% — never double-charge or double-refund |
| NFR-6 | Order availability | 99.95% |
| NFR-7 | Buybox lag | ≤ 5 s after offer / inventory change |
| NFR-8 | Catalog scale | 100M SKUs across 50M products and 1M sellers |
| NFR-9 | User scale | 100M registered, 30M MAU |
| NFR-10 | Peak QPS (sale day) | 100K search, 5K place-order, 50K cart-mutation, 1K capture |
| NFR-11 | Sustained QPS (normal day) | 10K search, 200 place-order |

---

## Out of scope (V1)

- Subscriptions / recurring orders ("Subscribe & Save").
- B2B / GST invoicing flow (separate billing engine).
- Try-before-you-buy / wardrobe-style flows.
- Group buying / referral commerce.
- AR / 3D product previews.
- Live shopping / influencer-led commerce.
- International cross-border (single country, single currency in V1).
- Loyalty / rewards points (separate ledger).
- Recommendations / personalisation engine (treat as black box if it exists).
- Seller financing / advances on payouts.

---

## Edge cases the requirements MUST cover

- **Last-unit race**: two buyers, same offer, last unit, simultaneous click → exactly one wins.
- **Idempotent place-order**: client retries the request → exactly one order.
- **Mid-saga failure**: inventory reserved, payment auth fails → release inventory, no order.
- **Cart staleness**: price changed since add-to-cart → warn at checkout, charge today's price (or buyer cancels).
- **Cross-seller partial fail**: 5 cart lines from 5 sellers; 1 seller out of stock → entire order rejected (we don't partial-place; cleaner UX).
- **Authorize succeeds, persist fails**: gateway holds money but no order in DB → reconciliation job voids orphan auths.
- **Capture after auth window**: shipment dispatches 30 days after order; gateway auth lapsed → MIT fallback.
- **Refund of partially-captured order**: order had 3 shipments, 2 captured + delivered, 3rd never shipped → refund only the 3rd's amount.
- **Seller suspended mid-order**: order has 3 items from sellerX; sellerX's account suspended → ops re-routes or refunds those lines.
- **Inventory webhook drift**: seller's external system reports 0; we have 1 reserved; resolve by holding the existing reservation but blocking new ones.
- **Buyer-initiated cancel race vs dispatch**: buyer taps Cancel while seller's worker is marking SHIPPED → status-guarded UPDATE wins atomically.
- **Returned item lost in reverse logistics**: return APPROVED, never INSPECTED → SLA-driven auto-refund.

---

## Output

```
Catalog:        Product → SKU → ListingOffer (per-seller inventory)
Inventory:      one row per (seller_id, sku_id), atomic decrement via CAS
Cart:           soft, no reservation; price_at_add tracked
Order:          place-order saga (idempotent) → CONFIRMED
Shipments:      one per seller; per-shipment capture; partial cancel/refund
Payment:        AUTH at order, CAPTURE at ship, REFUND at return/cancel
Returns:        async aggregate, post-delivery, optional restock
Buybox:         strategy-driven, eventually consistent, explainable
Hard rules:     never oversell, never double-charge, never double-refund
```
