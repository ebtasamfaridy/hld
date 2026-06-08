# 00 · E-Commerce — Simple Language Problem Statement

> Read this **before** `01_requirements.md`. The goal is to give you the gut-feel of what we're building, in plain language.

---

## The story

Aman opens the Amazon app on a Sunday afternoon. He's looking for an iPhone 15, a phone case, and a USB-C charger.

He searches "iPhone 15 256GB". The results page shows the phone with a price and a small line: *"Sold by Acme Mobiles · Fulfilled by Amazon · Delivers Tuesday"*. Below the headline price he sees *"Other sellers: 3 from ₹74,999"*. He taps "Add to Cart". He searches the case and the charger, taps "Add to Cart" on each. His cart now has three items — the iPhone from Acme Mobiles, a case from CaseHub, and a charger from PowerPlus.

He taps **Buy Now**. The checkout page asks for the shipping address (his Bangalore home), shows the breakdown — phone ₹79,900 + case ₹1,200 + charger ₹2,500 = ₹83,600. He picks "Pay with saved card", reviews, and taps **Place Order**.

In the next 800 ms, a lot happens. The system **atomically decrements one unit** from each of the three sellers' inventory. It **authorizes ₹83,600** on Aman's card. It writes one `Order` row, three `OrderItem` rows, and three `Shipment` rows — one per seller. It hands the order ID back to the app, which navigates to "Order placed".

Tuesday morning the iPhone ships from Acme Mobiles' warehouse. Tuesday afternoon the case ships from CaseHub. Wednesday the charger ships from PowerPlus. Each shipment fires its own *capture* on the gateway: Acme's portion (₹79,900), then CaseHub's (₹1,200), then PowerPlus's (₹2,500) — three separate captures against the same authorization.

Wednesday evening Aman receives the iPhone. Friday he receives the case. The charger gets lost in transit; the courier marks it "exception". A week later, customer support refunds ₹2,500 against the original authorization. The order's status is now *partially refunded*; the other two shipments are *delivered*. Aman is happy enough.

That whole flow looks simple. Underneath it has a dozen tricky problems:

- **Two users tap "Place Order"** on the last unit of the iPhone at the same millisecond. Only one wins.
- **Aman's payment authorize succeeds** but the database write fails — without compensation, his card is held but no order exists.
- **One seller can't fulfil** mid-saga. We can't unwind without confusing Aman.
- **The iPhone listing has 3 sellers** at different prices. Which offer does the system pick by default? (The "buybox.")
- **Aman wants to cancel** after the iPhone shipped but before the case shipped. Partial cancel.
- **Aman returns the iPhone** ten days later. We have to refund ₹79,900 *to the same card* days after the original capture.

That's what we're designing.

---

## What is the user actually trying to do?

**Aman (the buyer):**
- "Search for products. Show me prices, ratings, delivery dates, photos."
- "Compare offers from different sellers."
- "Build a cart. Save items for later."
- "Check out. Pay once. Confirm an order."
- "Track each shipment. Cancel if it hasn't shipped."
- "Return what didn't fit. Get a refund."

**Acme Mobiles (the seller):**
- "List my SKUs with prices and stock counts."
- "See incoming orders. Print packing slips."
- "Mark items shipped with tracking numbers."
- "Get paid net-of-fees after delivery."
- "Process returns when items come back."

**Amazon Ops (the platform):**
- "Onboard sellers and products. Catalog hygiene."
- "Set the buybox rules — who wins when many sellers list the same SKU."
- "Run promotions. Manage refunds and disputes."
- "Reconcile money: charge buyer, pay seller, deduct platform fee."

---

## Walk through one concrete example

Catalog:
- **Product**: "Apple iPhone 15" — one logical entry; specs, reviews, primary image.
- **SKUs of that product**:
  - SKU-IP15-128-BLK (128 GB, Black)
  - SKU-IP15-256-BLK (256 GB, Black)
  - SKU-IP15-256-BLU (256 GB, Blue)
- **Listing offers for SKU-IP15-256-BLK**:
  - Acme Mobiles · ₹79,900 · 5 in stock · ships next day · seller rating 4.7
  - PhoneBazaar · ₹74,999 · 1 in stock · ships in 3 days · seller rating 4.2
  - QuickCells · ₹78,000 · 2 in stock · ships in 5 days · seller rating 4.0

The buybox picks Acme as default — best score across price, SLA, and rating. The "3 other sellers" line is the alternates.

1. Aman searches "iPhone 15". Search returns Products (logical). He picks the 256 GB Black variant (SKU). Buybox shows Acme.
2. Adds to cart. Cart row stores `(user, product_id, sku_id, offer_id, qty=1, price_at_add=79900)`. Nothing reserved.
3. Aman builds out his cart with case + charger from two other sellers.
4. Taps Place Order. Server runs the saga:
    - Idempotency lookup on `(user_id, idempotency_key)` → not present, proceed.
    - For each cart line, atomically decrement inventory: `UPDATE inventory_units SET available = available - 1 WHERE seller_id=$s AND sku_id=$k AND available >= 1`. If any row affects 0, rollback and 409 OUT_OF_STOCK.
    - Authorize ₹83,600 on Aman's card. Idempotency key = `order_id`.
    - Persist Order + 3 OrderItem + 3 Shipment + Payment + outbox in one TXN.
    - Return 201 with order id.
5. Each seller gets a "new order" notification (Kafka → seller dashboard).
6. Acme picks/packs/ships in 8 hours. Marks shipment SHIPPED with AWB. Fulfilment service captures ₹79,900 on the gateway.
7. CaseHub ships the case the same evening; capture ₹1,200.
8. PowerPlus tries to ship the charger; their stock-keeper finds it broken. Marks shipment CANCELLED. Refund ₹2,500 against the same authorization (still within the void window).
9. Aman's order status is "partially shipped". Two shipments deliver. The cancelled one is auto-refunded.
10. Ten days later, Aman returns the iPhone (didn't like the screen). Return aggregate created. Acme's warehouse inspects → approves. Refund of ₹79,900 issued — this time as a *capture refund*, against the captured charge. Settlement adjusts: we claw back from Acme's pending payout.

---

## What's tricky about this (and why we need an LLD at all)

1. **Three-layer catalog.** Product (logical), SKU (variant), ListingOffer (per-seller, the inventory-bearing thing). Confusing names, easy to conflate. Get this wrong and your inventory math is wrong forever.
2. **Per-seller inventory atomicity.** Two buyers race for the last unit of `(Acme, SKU-IP15-256-BLK)`. Same problem as BookMyShow's last seat. Solution is the same: `UPDATE … WHERE available >= qty`.
3. **The cart-to-order saga.** N items × M sellers × payment authorize. Each step is a remote-ish call; failures must compensate.
4. **Multi-shipment fulfilment.** One order, multiple sellers, multiple boxes, multiple tracking numbers. The order's "shipped" / "delivered" status is derived from the children, not stored independently.
5. **Two-phase payment with per-shipment capture.** Authorize once at order, capture in pieces as each shipment goes out. Sum of captures ≤ authorized.
6. **Buybox.** Picking which offer wins when many sellers list the same SKU. Multi-criteria scoring; explainable; updated when offer/inventory changes.
7. **Returns and refunds days after capture.** A return-refund is a different operation from an authorize-void; it goes against the *captured* charge, not the original auth. Requires `(captured_at + N days)` window logic and saved payment method.
8. **Cart staleness.** Aman added an item at ₹79,900 yesterday; today the price is ₹74,999. Do we honour his price or charge today's price? Standard answer: at-checkout pricing wins; we warn him "price changed".
9. **Inventory drift.** Sellers update stock from external systems; we reconcile via webhooks + nightly recon. Drift between our count and theirs causes apparent oversells.
10. **Idempotency at every layer.** Place-order is idempotent on `(user_id, key)`; payment auth on `order_id`; capture on `shipment_id`; refund on `(captured_id, refund_id)`.

These appear in `04_domain_model.md`, `05_database_design.md`, `08_sequence_diagrams.md`, and `11_concurrency_and_scaling.md`.

---

## Mental model in one line

> **An e-commerce marketplace is a three-layer catalog (Product → SKU → ListingOffer) sitting on top of per-offer inventory rows, where the cart-to-order saga atomically decrements one unit per offer, authorizes the buyer once, and splits fulfilment + capture per seller, with returns and refunds running as a separate aggregate post-delivery.**

---

## Where to go next

→ Open [`01_requirements.md`](./01_requirements.md). You'll see this story formalised into FRs / NFRs covering catalog, search, cart, checkout, payment, fulfilment, cancellation, returns, and seller operations.
