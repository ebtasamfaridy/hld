# 06 · E-Commerce — API Design

## Conventions

- REST + JSON over HTTPS.
- All write endpoints accept `Idempotency-Key` (24 h dedup window).
- Versioned: `/v1/...`.
- RFC 7807 problem+json for errors.
- Pagination via `cursor` + `limit`.
- Currency in minor units (paise/cents).

---

## Search & catalog

### Search

```
GET /v1/search?q=iphone+15&filters=category:electronics,brand:apple&sort=relevance&cursor=&limit=20
→ 200
{
  "results": [
    {
      "productId": "...",
      "title": "Apple iPhone 15",
      "brand": "Apple",
      "primaryPhoto": "https://cdn/...",
      "rating": 4.5,
      "reviewCount": 12345,
      "buybox": {
        "offerId": "...",
        "sellerId": "...",
        "sellerName": "Acme Mobiles",
        "price": { "minor": 7990000, "currency": "INR" },
        "shipsInDays": 1,
        "primeEligible": true
      },
      "alternateOffersCount": 2
    }
  ],
  "facets": {
    "brand": [{"value":"Apple","count":12},{"value":"Samsung","count":8}],
    "priceBuckets": [...]
  },
  "nextCursor": "eyJ..."
}
```

### Product detail

```
GET /v1/products/{productId}
→ 200
{
  "productId": "...",
  "title": "Apple iPhone 15",
  "description": "...",
  "specs": {...},
  "photos": [...],
  "skus": [
    {
      "skuId": "...",
      "variantAttrs": { "storage": "256GB", "color": "Black" },
      "buybox": {...},
      "otherOffers": [...]
    }
  ]
}
```

### List offers for a SKU

```
GET /v1/skus/{skuId}/offers
→ 200
{
  "offers": [
    { "offerId":"...", "sellerId":"...", "price":{...}, "shipsInDays":1, "rating":4.7 },
    ...
  ]
}
```

---

## Cart

### Get my cart

```
GET /v1/cart
→ 200
{
  "lines": [
    {
      "offerId": "...",
      "skuId": "...",
      "productTitle": "Apple iPhone 15",
      "qty": 1,
      "priceAtAdd": { "minor": 7990000 },
      "currentPrice": { "minor": 7990000 },
      "stale": false,
      "inStock": true
    }
  ],
  "subtotal": { "minor": 8360000, "currency": "INR" }
}
```

### Add to cart

```
POST /v1/cart/items
Idempotency-Key: <uuid>
{ "offerId": "...", "qty": 1 }
→ 201 (line added or qty incremented)
{ "lineId": "...", "qty": 1, "priceAtAdd": {...} }

→ 410 OFFER_INACTIVE     (offer was deleted/suspended)
→ 422 INVALID_QTY        (qty > 0 required)
```

### Update / remove line

```
PATCH /v1/cart/items/{offerId}
{ "qty": 2 }
→ 200

DELETE /v1/cart/items/{offerId}
→ 204
```

### Clear cart

```
DELETE /v1/cart
→ 204
```

---

## Place order (the saga)

```
POST /v1/orders
Idempotency-Key: <uuid>
{
  "cartId":          "implicit-from-user",
  "addressId":       "...",
  "paymentMethodId": "...",
  "promoCode":       "BIGBILLION10"
}

→ 201 (success)
{
  "orderId": "...",
  "status": "CONFIRMED",
  "total":  { "minor": 8360000, "currency": "INR" },
  "shipments": [
    { "shipmentId":"...", "sellerId":"...", "amount":{...}, "expectedDispatchAt":"..." },
    { "shipmentId":"...", "sellerId":"...", "amount":{...}, "expectedDispatchAt":"..." }
  ],
  "payment": { "authorizationId": "...", "status":"AUTHORIZED" }
}

→ 409 OUT_OF_STOCK         { "blockedLines": [{"offerId":"...","available":0}] }
→ 409 PRICE_CHANGED        { "lines": [{"offerId":"...","oldPrice":{...},"newPrice":{...}}] }
→ 402 PAYMENT_DECLINED     { "reason": "INSUFFICIENT_FUNDS" }
→ 422 ADDRESS_INVALID
→ 409 IDEMPOTENCY_CONFLICT (same key, different payload)
→ 423 USER_BLOCKED         (e.g. fraud, dunning)
```

### Get order

```
GET /v1/orders/{orderId}
→ 200
{
  "orderId": "...",
  "status": "IN_FULFILMENT",
  "total": {...},
  "items": [...],
  "shipments": [
    {
      "shipmentId":"...","status":"DISPATCHED","awb":"...",
      "carrier":"BlueDart","dispatchedAt":"...",
      "items":[{"orderItemId":"...","skuId":"...","qty":1}]
    }
  ],
  "payment": {
    "authorizedMinor": 8360000,
    "capturedMinor": 7990000,
    "refundedMinor": 0
  },
  "createdAt":"..."
}
```

### List my orders

```
GET /v1/orders?status=IN_FULFILMENT,COMPLETED&cursor=&limit=20
→ 200 [orders...]
```

---

## Cancellation

### Cancel order (full)

```
POST /v1/orders/{id}/cancel
Idempotency-Key: <uuid>
{ "reason": "PLAN_CHANGED" }
→ 200
{
  "status": "CANCELLED",
  "refunds": [
    { "shipmentId":"...","amount":{...},"kind":"AUTH_VOID" },
    { "shipmentId":"...","amount":{...},"kind":"CAPTURE_REFUND" }
  ]
}

→ 409 SHIPMENT_ALREADY_DISPATCHED   (must use partial-cancel or returns flow)
→ 410 ALREADY_CANCELLED
```

### Cancel a single shipment

```
POST /v1/shipments/{id}/cancel
Idempotency-Key: <uuid>
{ "reason": "WRONG_SIZE" }
→ 200 { "shipmentStatus":"CANCELLED", "refundMinor": 120000, "refundKind":"AUTH_VOID" }

→ 409 ALREADY_OUT_FOR_DELIVERY
```

---

## Returns

### Request a return

```
POST /v1/returns
Idempotency-Key: <uuid>
{
  "shipmentId": "...",
  "orderItemIds": ["..."],
  "reason": "DEFECTIVE",
  "notes": "Charging port loose"
}
→ 201 { "returnId":"...", "status":"REQUESTED", "pickupScheduledFor":"..." }

→ 409 OUT_OF_RETURN_WINDOW    { "windowDays": 7 }
→ 409 NOT_DELIVERED
→ 409 ALREADY_RETURNED
```

### Approve / inspect / refund (ops + warehouse)

```
POST /v1/returns/{id}/approve            { "reviewerId":"..." } → 200
POST /v1/returns/{id}/inspect            { "passed": true, "restock": true, "notes":"..." } → 200

(refund issued automatically on inspect.passed=true; status → REFUNDED)
```

---

## Payments — webhooks

### Gateway

```
POST /webhooks/payments
X-Signature: hmac-sha256=...
{ "eventId":"evt_...","type":"payment.captured","gatewayRef":"ch_...","amountMinor":799000 }
→ 200 (we dedupe by eventId)
```

### Carrier (shipment status)

```
POST /webhooks/carrier/{carrier}
X-Signature: ...
{ "eventId":"...","awb":"...","status":"DELIVERED","ts":"..." }
→ 200
```

---

## Seller / operator API

### Catalog

```
POST /v1/seller/offers
{ "skuId":"...","priceMinor":799000,"shipsInDays":1,"available":5 }
→ 201

PATCH /v1/seller/offers/{offerId}
{ "priceMinor":779000,"available":3 }
→ 200

DELETE /v1/seller/offers/{offerId}
→ 204
```

### Inventory adjust (additive)

```
POST /v1/seller/inventory/adjust
Idempotency-Key: <uuid>
{ "skuId":"...","delta":+10,"reason":"RESTOCK" }
→ 200 { "newAvailable": 13 }
```

### Mark shipment packed / dispatched

```
POST /v1/seller/shipments/{id}/pack       → 200 { "status":"PACKED" }
POST /v1/seller/shipments/{id}/dispatch
{ "awb":"...","carrier":"BlueDart" }      → 200 { "status":"DISPATCHED","capturedMinor":799000 }
```

### Suspend seller (ops)

```
POST /v1/operator/sellers/{id}/suspend
{ "reason":"COMPLIANCE_VIOLATION" }
→ 200
```

---

## Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `OUT_OF_STOCK` | 409 | Inventory CAS failed at place-order |
| `PRICE_CHANGED` | 409 | Cart line price differs from current |
| `IDEMPOTENCY_CONFLICT` | 409 | Same key, different payload |
| `OFFER_INACTIVE` | 410 | Offer suspended/deleted |
| `OUT_OF_RETURN_WINDOW` | 409 | Past return SLA |
| `SHIPMENT_ALREADY_DISPATCHED` | 409 | Cancel arrived too late |
| `PAYMENT_DECLINED` | 402 | Gateway rejected |
| `USER_BLOCKED` | 423 | Fraud / dunning |
| `ADDRESS_INVALID` | 422 | Failed address validation |
| `CAPTURE_AUTH_EXPIRED` | 503 | Auth window lapsed; falling back to MIT |
| `REFUND_FAILED` | 503 | Gateway refund retry path |

---

## Idempotency rules

- Place order / cancel / return create → `UNIQUE(user_id, idempotency_key)`.
- Capture (per shipment) → idempotency-key tied to `shipment_id`.
- Refund (per cancel/return) → idempotency-key tied to `(cancel_id | return_id)`.
- Inventory adjust → `UNIQUE(seller_id, idempotency_key)`.
- Webhooks → `processed_events(eventId)`.

---

## Output

```
Browse:     GET search, GET product / SKU detail (cached, eventually consistent)
Cart:       Redis-backed CRUD, no inventory reservation
Order:      POST /orders (idempotent saga: inv decrement + payment auth + persist)
Lifecycle:  POST shipments dispatch (per-shipment capture), POST cancel, POST returns
Refunds:    routed to AUTH void or CAPTURE refund; idempotent
Webhooks:   gateway + carrier, both eventId-deduped
Seller:     offer CRUD, inventory adjust, ship operations
```
