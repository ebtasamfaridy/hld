# 10 · E-Commerce — Design Patterns

## Patterns at play

| Pattern | Where | Why |
| --- | --- | --- |
| **Saga (orchestrator)** | `OrderService.place(...)` | Multi-step, multi-service transaction with compensations |
| **Strategy** | `BuyBoxStrategy`, `CancellationPolicy`, `ReturnPolicy`, `CarrierAdapter`, `PricingComponent` | Pluggable behaviour |
| **Composite** | Pricing components for promo / shipping / tax | Final amount is composed |
| **Repository** | All aggregates | Hide persistence |
| **Aggregate root (DDD)** | Order, ListingOffer, Cart, Return | Enforce invariants |
| **Value object** | `Money`, `Address`, `OfferScore`, `Idempotency-Key` | Immutable, equality-by-value |
| **Outbox** | OrderDB → Kafka | Reliable event publish in same TXN |
| **State pattern (light)** | Order, Shipment, Payment, Return — enum + transition validators | State changes guarded by enum-based rules; behaviour stays in services |
| **Idempotency key** | All money-bearing endpoints | Safe retry |
| **Sealed types / ADT** | `ReserveResult` = `Reserved \| Conflict` | Two-path success without exceptions |
| **Specification** | Cart-line eligibility, return window check, buybox in-stock filter | Composable boolean rules |
| **Observer / Pub-Sub** | Domain events → notification, search index, payout, recon | Loose coupling via Kafka |
| **Circuit Breaker / Bulkhead** | Payment gateway, carrier APIs, seller webhooks | Fail fast when downstream sick |
| **Two-phase payment** | AUTH at order, CAPTURE at ship, MIT/REFUND for variances | Decouple intent from money movement |
| **Adapter** | Per-carrier shipping, per-gateway payment, per-seller webhook | Vendor diversity |
| **CQRS-lite** | Browse plane (ES) vs Buy plane (Postgres) — read model derived via CDC | Independent scaling |

---

## Saga in detail

`OrderService.place(...)` is the orchestrator:

| Step | What | Compensation |
| --- | --- | --- |
| 1 | Idempotency lookup | (none — read only) |
| 2 | Hydrate cart + recompute prices | (none) |
| 3 | Validate address + user (eligibility, dunning) | (none) |
| 4 | `inventory.reserve(lines)` — atomic CAS per line | `inventory.release(lines)` |
| 5 | `payment.authorize(total)` — gateway hold | gateway voidAuth |
| 6 | Persist Order + OrderItems + Shipments + Payment + outbox in one TXN | DB ROLLBACK |
| 7 | Return 201 | (none) |

Crucial: **payment authorize happens *outside* the inventory TXN**. That avoids holding row locks across an external HTTPS call. If steps 5 or 6 fail after step 4, we explicitly compensate step 4.

Same shape as the Car Rental saga; the difference is N inventory rows (one per cart line) instead of N timeslot rows.

---

## Composite pricing

Final amount construction is a textbook Composite:

```java
List<PricingComponent> components = List.of(
    new SubtotalComponent(),       // sum of line items
    new ShippingComponent(),       // per-shipment shipping
    new PromoDiscountComponent(),  // promo code, sale, etc.
    new TaxComponent(),            // GST per line
    new BuyerProtectionComponent() // optional (e.g. assured purchase)
);
CompositePricing pricing = new CompositePricing(components);
```

Adding a "platinum-membership discount" or "regional surcharge" is **a new component**, registered in the list. Existing code is untouched. Each component returns `Money.zero` if it doesn't apply.

Order matters: discounts come after subtotal but before tax (or after, depending on tax rules), and shipping is its own line.

---

## Idempotency design

| Operation | Key | Constraint location |
| --- | --- | --- |
| Place order | client UUID | `UNIQUE(user_id, idempotency_key)` on orders |
| Cancel order / shipment | client UUID | UNIQUE on cancel-events table |
| Authorize | order_id | gateway dedupes |
| Capture (per shipment) | shipment_id | gateway dedupes; UNIQUE on captures |
| Refund (per cancel/return) | cancel_id or return_id | gateway dedupes; UNIQUE on refunds |
| Inventory adjust | UNIQUE(seller_id, idempotency_key) | App-level dedup |
| Webhook events | gateway eventId | UNIQUE on `processed_events` |
| Buyer-initiated cart mutate | client UUID | optional UNIQUE on `cart_events` |

Two layers protect from double-charging the user: our own UNIQUE constraints + the gateway's dedup on idempotency-key. Belt and suspenders.

---

## Outbox in this system

Place order writes order, items, shipments, payment, and `OrderPlaced` event into the **same TXN**:

```sql
BEGIN;
INSERT INTO orders (...);
INSERT INTO order_items (...) × N;
INSERT INTO shipments  (...) × M;
INSERT INTO payments   (...);
INSERT INTO outbox(event_type, payload) VALUES ('OrderPlaced', $...);
COMMIT;
```

A separate publisher (Debezium CDC or polling worker) reads outbox rows and publishes to Kafka. If the publisher dies after publish but before marking — duplicates are deduped by consumers via `eventId`. If COMMIT fails, neither happens.

---

## Strategy in detail

### `BuyBoxStrategy`

```java
interface BuyBoxStrategy {
  ListingOffer pick(List<ListingOffer> candidates, BuyBoxContext ctx);
  String explain(ListingOffer winner, List<ListingOffer> candidates);
}
```

Implementations:
- `DefaultBuyBox` — weighted score on (price, SLA, rating, prime, in-stock).
- `LowestPriceBuyBox` — for promo campaigns.
- `PrimeFirstBuyBox` — prime-eligible always wins.
- `ExperimentalBuyBox` — A/B test wrapper that randomises among 2 strategies for a percentage of traffic.

The active strategy is per-category / per-sale config; resolved at recompute time.

### `CarrierAdapter`

```java
interface CarrierAdapter {
  AwbResult book(Shipment s);
  TrackingStatus track(String awb);
  RTOResult initiateRTO(String awb);
}
```

Implementations: `BlueDartAdapter`, `DelhiveryAdapter`, `IndiaPostAdapter`, `MockCarrier`. Picked by a registry keyed by `(seller_id, region)` or by ops policy.

### `CancellationPolicy` / `ReturnPolicy`

```java
interface CancellationPolicy {
  CancelOutcome decide(Shipment s, Instant now);
}

interface ReturnPolicy {
  boolean isEligible(Shipment s, Instant now);
  boolean isRestockable(Return r);
  int returnWindowDays();
}
```

Per-category overrides — perishables disallow return; jewellery has 24-hr inspect-on-delivery only; bulk electronics have a 30-day window. The policy is resolved per-line based on SKU's category.

---

## State pattern — why enum + table, not GoF

Same reasoning as car rental and the canonical tutorial (`00_End_To_End_LLD_Tutorial/05_design_patterns.md`):

- State is **persisted as a string** (column on the row). GoF would require serializing class identity.
- Behaviour for each state lives in **services** (cancel logic, refund routing, capture decision), not on the entity.
- The "interesting question" is *which transitions are allowed* — a `Map<S, Set<S>>` answers it in 5 lines.
- Adding a new state means adding the enum value + entry in the transition table. No new class hierarchy.

Where GoF State **would** be appropriate: if a single object had wildly different behaviour for the same operation in each state. That's not what e-commerce orders look like.

---

## Sealed types for inventory reservation

```java
public sealed interface ReserveResult permits ReserveResult.Reserved, ReserveResult.Conflict {
    record Reserved(List<Line> lines) implements ReserveResult {}
    record Conflict(List<Line> blocked) implements ReserveResult {}
}
```

The caller pattern-matches:

```java
return switch (inventory.reserve(lines, idemKey)) {
    case Reserved r -> proceedWithPayment(r);
    case Conflict c -> error(409, "OUT_OF_STOCK", c.blocked());
};
```

Throwing for the common-failure case (out of stock) is a code smell — it pollutes stack traces and hurts hot-path latency. Sealed types are idiomatic modern Java for "two paths, no exceptions."

---

## Refund routing — Strategy disguised as if/else

Cancellation routes refunds based on what already happened:

```java
RefundRoute route = switch (shipment.status()) {
    case PACKED, CREATED        -> RefundRoute.AUTH_PARTIAL_VOID;
    case DISPATCHED             -> shipment.captureId() != null
                                   ? RefundRoute.CAPTURE_REFUND
                                   : RefundRoute.AUTH_PARTIAL_VOID;
    case OUT_FOR_DELIVERY,
         DELIVERED              -> throw new IllegalStateException("use returns flow");
};
paymentService.refund(payment, shipment.amount(), route, idemKey);
```

Modeling this as a policy class (`RefundRouteStrategy`) makes sense once we have multiple gateways with different rules — not yet, so we keep it inline.

---

## CQRS-lite — browse vs buy

The browse plane is a **read model** derived from the buy plane via CDC. ES is the read store; CatalogDB + InventoryDB are the write stores.

| Aspect | Buy plane | Browse plane |
| --- | --- | --- |
| Reads | Order detail, cart, my-orders | Search, PDP, listings |
| Writes | Place order, dispatch, refund, inventory | Catalog edit (writes go to CatalogDB; ES learns via CDC) |
| Consistency | Strong | Eventual (≤ 30 s) |
| Failure | Cannot place order | Search degrades, fall back to direct PDP |

Place-order is the *only* place where consistency matters; everything else can drift briefly.

---

## Patterns we deliberately avoided

| Pattern | Why not |
| --- | --- |
| **2PC across our DB and gateway** | Gateway is external; saga + outbox + idempotency is the modern answer |
| **Holding DB locks during payment auth** | Auth can take 1 s; never hold locks across user/external waits |
| **Microservices per state-machine entity** | Order, Shipment, Payment can live in one service for V1; split if scaling pain demands |
| **Storing raw card numbers** | Vault tokens only; card data never touches our DB |
| **"Soft hold" inventory in cart** | Surprises buyers ("how is it out of stock if I had it in cart?"); we hold only at place-order |
| **Single inventory counter per SKU** | Per-seller is the marketplace truth; aggregating loses the multi-seller dimension |

---

## Output

```
Saga + outbox + idempotency = correctness spine
Composite pricing            = subtotal/shipping/promo/tax/protection
Strategy                     = BuyBox, CarrierAdapter, CancellationPolicy, ReturnPolicy
Sealed ADT                   = ReserveResult (Reserved | Conflict) — exception-free
Two-phase payment            = AUTH → CAPTURE per shipment; MIT for late capture; refund routes
Enum + transition table      = state machines without class explosion
Adapter                      = pluggable gateway / carrier / seller webhook
CQRS-lite                    = browse (ES) vs buy (Postgres) — independent scaling
Circuit breaker + bulkhead   = isolate gateway / carrier / seller-webhook outages
```
