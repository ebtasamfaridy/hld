# 08 · E-Commerce — Sequence Diagrams

## 1. Search → product detail

```mermaid
sequenceDiagram
    autonumber
    participant U as Buyer
    participant API
    participant SS as SearchSvc
    participant ES as Elasticsearch
    participant BB as BuyBoxSvc
    participant Redis

    U->>API: GET /search?q=iphone+15
    API->>SS: search
    SS->>ES: query
    ES-->>SS: top productIds
    SS->>BB: buyboxFor(skuIds[])
    BB->>Redis: MGET bb:sku:*
    Redis-->>BB: winningOfferIds (with TTL miss = recompute)
    BB-->>SS: offers (price, ships-in, seller name)
    SS-->>API: enriched results
    API-->>U: 200 results
```

The ES index is refreshed via CDC from `listing_offers` and `inventory_units` every ~30 s. Search is intentionally eventually consistent — place-order is the consistency oracle.

---

## 2. Add to cart

```mermaid
sequenceDiagram
    autonumber
    participant U as Buyer
    participant API
    participant CS as CartSvc
    participant Cat as CatalogSvc
    participant Redis
    participant DB

    U->>API: POST /cart/items {offerId, qty}
    API->>CS: add
    CS->>Cat: getOffer(offerId)
    Cat-->>CS: offer (status=ACTIVE, price)
    alt offer inactive
        CS-->>API: 410 OFFER_INACTIVE
    end
    CS->>Redis: HSET cart:userId offerId {qty,priceAtAdd}
    CS->>DB: UPSERT cart_lines (async, fire-and-forget)
    CS-->>API: 201 lineCreated
```

Cart never touches inventory — that's at place-order.

---

## 3. Place order — happy path

```mermaid
sequenceDiagram
    autonumber
    participant U as Buyer
    participant API
    participant OS as OrderSvc
    participant CS as CartSvc
    participant Inv as InventorySvc
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant DB
    participant K as Kafka

    U->>API: POST /orders {idemKey, addressId, paymentMethodId}
    API->>OS: place

    OS->>DB: SELECT orders WHERE user_id=u AND idempotency_key=k
    alt already exists
        OS-->>API: 200 cached order
    end

    OS->>CS: hydrateCart(userId)
    CS-->>OS: lines [(offerId, qty, priceAtAdd)]
    OS->>OS: recompute prices, surface stale lines
    OS->>OS: group lines by seller_id → shipments

    OS->>Inv: reserve(lines, idemKey)
    Inv->>DB: BEGIN
    loop per line
        Inv->>DB: UPDATE inventory_units SET available=available-qty WHERE seller=$s AND sku=$k AND available>=qty
        alt 0 rows affected
            Inv->>DB: ROLLBACK + compensate prior decrements
            Inv-->>OS: Conflict(blockedLines)
            OS-->>API: 409 OUT_OF_STOCK
        end
    end
    Inv->>DB: COMMIT
    Inv-->>OS: Reserved(lines)

    OS->>Pay: authorize(orderId, total, idemKey=orderId)
    Pay->>GW: AUTH
    GW-->>Pay: authId
    Pay-->>OS: AUTHORIZED

    OS->>DB: BEGIN
    OS->>DB: INSERT orders (status=CONFIRMED) ON CONFLICT (user,idemKey) DO NOTHING
    OS->>DB: INSERT order_items × N
    OS->>DB: INSERT shipments × M
    OS->>DB: INSERT payments (status=AUTHORIZED)
    OS->>DB: INSERT outbox(OrderPlaced)
    OS->>DB: COMMIT

    Note over DB,K: Outbox → Kafka publishes OrderPlaced
    K-->>U: (later) confirmation email

    OS-->>API: 201 order
    API-->>U: 201
```

---

## 4. Place order — inventory conflict (rollback)

```mermaid
sequenceDiagram
    autonumber
    participant OS as OrderSvc
    participant Inv as InventorySvc
    participant DB

    OS->>Inv: reserve([line1, line2, line3], idemKey)
    Inv->>DB: BEGIN
    Inv->>DB: UPDATE inventory_units (line1) -- 1 row
    Inv->>DB: UPDATE inventory_units (line2) -- 1 row
    Inv->>DB: UPDATE inventory_units (line3) -- 0 rows (out of stock)
    Inv->>DB: -- compensate: re-increment line1, line2
    Inv->>DB: ROLLBACK
    Inv-->>OS: Conflict(blocked=[line3])

    Note over OS: No payment auth attempted, no order rows persisted
    OS-->>OS: respond 409 OUT_OF_STOCK with line3 details
```

We use ROLLBACK for the entire savepoint (cleaner than manual compensation) — the BEGIN/ROLLBACK around all UPDATEs ensures atomicity in Postgres.

---

## 5. Place order — payment declined

```mermaid
sequenceDiagram
    autonumber
    participant OS as OrderSvc
    participant Inv as InventorySvc
    participant Pay as PaymentSvc
    participant GW as Gateway

    OS->>Inv: reserve(lines)
    Inv-->>OS: Reserved

    OS->>Pay: authorize(orderId, total, idemKey)
    Pay->>GW: AUTH
    GW-->>Pay: DECLINED (INSUFFICIENT_FUNDS)
    Pay-->>OS: PAYMENT_DECLINED

    OS->>Inv: release(orderId, lines)
    Inv->>Inv: UPDATE inventory_units SET available=available+qty (per line)

    OS-->>OS: respond 402 PAYMENT_DECLINED
```

The compensation here is a *separate* TXN (the reserve TXN already committed). The compensating UPDATE is itself idempotent because we key it by `orderId` in a held-lines table or via the outbox.

---

## 6. Shipment dispatch (capture)

```mermaid
sequenceDiagram
    autonumber
    participant Seller
    participant API
    participant FS as FulfilmentSvc
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant DB
    participant K as Kafka

    Seller->>API: POST /seller/shipments/{id}/dispatch {awb, carrier}
    API->>FS: dispatch

    FS->>DB: SELECT shipment, payment
    FS->>FS: validate shipment.status = PACKED

    FS->>Pay: capture(authId, amount, idemKey=shipmentId)
    alt auth window valid
        Pay->>GW: CAPTURE
        GW-->>Pay: captureId
    else auth expired
        Pay->>GW: MIT(savedMethodToken, amount, idemKey=shipmentId)
        GW-->>Pay: chargeId
    end
    Pay-->>FS: CAPTURED

    FS->>DB: BEGIN
    FS->>DB: INSERT capture (idempotency_key=shipmentId) ON CONFLICT DO NOTHING
    FS->>DB: UPDATE shipment status=DISPATCHED, awb, capture_id WHERE id=? AND status='PACKED'
    FS->>DB: UPDATE payment captured_minor += amount
    FS->>DB: INSERT outbox(ShipmentDispatched)
    FS->>DB: COMMIT

    FS-->>API: 200 {status:DISPATCHED, capturedMinor}
```

---

## 7. Cancel shipment (auth still open)

```mermaid
sequenceDiagram
    autonumber
    participant U as Buyer
    participant API
    participant OS as OrderSvc
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant Inv as InventorySvc
    participant K as Kafka

    U->>API: POST /shipments/{id}/cancel
    API->>OS: cancelShipment

    OS->>OS: load shipment + payment
    alt shipment status > DISPATCHED
        OS-->>API: 409 ALREADY_OUT_FOR_DELIVERY
    end

    alt shipment captured already
        OS->>Pay: refund(captureId, amount, idemKey=cancelId)
        Pay->>GW: REFUND
    else not yet captured
        OS->>Pay: voidPartialAuth(authId, amount, idemKey=cancelId)
        Pay->>GW: PARTIAL_VOID
    end
    Pay-->>OS: REFUND/VOID OK

    OS->>Inv: increment(seller_id, sku_id, qty) per item
    Inv->>Inv: UPDATE inventory_units SET available=available+qty

    OS->>OS: status=CANCELLED, refund recorded
    OS-->>K: outbox(ShipmentCancelled)
    OS-->>API: 200 {refundMinor}
```

Cancellation routes the refund based on whether the shipment was already captured or still in AUTH state. Inventory is restored regardless.

---

## 8. Return → refund

```mermaid
sequenceDiagram
    autonumber
    participant U as Buyer
    participant API
    participant RS as ReturnSvc
    participant Carrier
    participant Wh as Warehouse
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant Inv as InventorySvc
    participant DB
    participant K as Kafka

    U->>API: POST /returns {shipmentId, items, reason}
    API->>RS: request
    RS->>RS: validate shipment.delivered + within window
    RS->>DB: INSERT return (status=REQUESTED)
    RS->>Carrier: schedule pickup
    RS-->>K: outbox(ReturnRequested)
    RS-->>API: 201 {returnId, status:REQUESTED}

    Carrier-->>Wh: package picked up + delivered
    Wh->>API: POST /returns/{id}/inspect {passed:true, restock:true}
    API->>RS: inspect

    RS->>DB: UPDATE return status=INSPECTED
    RS->>Pay: refundForReturn(captureId, amount, idemKey=returnId)
    Pay->>GW: REFUND
    GW-->>Pay: refundId
    Pay-->>RS: REFUNDED

    alt restock
        RS->>Inv: increment(seller_id, sku_id, qty)
    end

    RS->>DB: UPDATE return status=REFUNDED, refund_id=...
    RS-->>K: outbox(ReturnRefunded)
```

---

## 9. Webhook — payment captured

```mermaid
sequenceDiagram
    autonumber
    participant GW as Gateway
    participant API
    participant Pay as PaymentSvc
    participant DB

    GW->>API: POST /webhooks/payments {eventId, type, gatewayRef}
    API->>API: verify HMAC signature
    API->>DB: INSERT processed_events (eventId) ON CONFLICT DO NOTHING
    alt already processed
        API-->>GW: 200 (no-op)
    end
    API->>Pay: handle(event)
    Pay->>DB: BEGIN
    Pay->>DB: SELECT capture WHERE gateway_ref=...
    Pay->>DB: UPDATE capture status=CAPTURED (or align)
    Pay->>DB: COMMIT
    API-->>GW: 200
```

Webhook idempotency: dedupe by `eventId`; the actual data update is also idempotent because we key by gateway_ref.

---

## 10. Reconciliation — orphan auth sweep

```mermaid
sequenceDiagram
    participant Cron
    participant DB
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant K as Kafka

    Note over Cron: every 5 min
    Cron->>DB: SELECT auth records older than 30 min with no order row
    loop per orphan
        Cron->>Pay: voidAuth(authId, idemKey=authId)
        Pay->>GW: VOID
        GW-->>Pay: ok
        Cron->>DB: mark auth voided
        Cron-->>K: outbox(OrphanAuthVoided)
    end
```

Catches the rare case where authorize succeeded but the orders TXN failed.

---

## 11. BuyBox recompute

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka
    participant BB as BuyBoxSvc
    participant DB
    participant Redis

    Note over K: OfferUpdated or InventoryUpdated event
    K->>BB: event(skuId, sellerId)
    BB->>DB: SELECT active offers WHERE sku_id=$sku
    BB->>BB: score each offer (price, sla, rating)
    BB->>BB: pick winner
    BB->>Redis: SET bb:sku:$sku winnerOfferId, expire=60s
    BB->>DB: UPSERT buybox_winners
    BB-->>K: outbox(BuyBoxChanged) (optional)
```

Recompute is debounced per SKU — multiple events within 1 s collapse to one recompute.

---

## What these reveal

- **Place order** is the central saga: idempotency, atomic per-line CAS, payment auth, persist + outbox.
- **Inventory conflict** rolls back cleanly — no payment attempted, no rows persisted.
- **Shipment dispatch** captures synchronously per shipment; auth-window expiry falls back to MIT.
- **Cancel** routes refund through void-or-refund based on capture state.
- **Returns** are explicitly async; refund happens only after warehouse inspection.
- **Webhooks** dedupe by `eventId`; downstream updates are idempotent.
- **Reconciliation** catches the half-states sagas leave behind.
- **BuyBox** is event-driven and eventually consistent within 5 s.
