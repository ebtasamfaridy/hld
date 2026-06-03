# 06 · Food Delivery — API Design

> All endpoints are versioned under `/v1`. All mutating endpoints accept an `Idempotency-Key` header. All responses use the `data` / `error` envelope.

---

## Customer-facing endpoints

### List nearby restaurants

```
GET /v1/restaurants?lat=12.97&lng=77.59&cuisine=indian&cursor=...&limit=20
Authorization: Bearer <jwt>

200 OK
{
  "data": [
    { "id": "r_1", "name": "Bowl Cafe", "cuisine": ["indian"],
      "rating": 4.3, "eta_minutes": 28, "distance_km": 1.4 }
  ],
  "next_cursor": "..."
}
```

### Get restaurant menu

```
GET /v1/restaurants/{restaurantId}/menu
Cache-Control: max-age=60
ETag: "v42"

200 OK
{
  "data": {
    "restaurant_id": "r_1",
    "categories": [
      { "id": "c_1", "name": "Starters",
        "items": [
          { "id": "m_9", "name": "Veg Manchurian", "price": "180.00", "available": true }
        ]
      }
    ],
    "version": 42
  }
}
```

ETag enables 304 Not Modified for repeat browses.

### Place order

```
POST /v1/orders
Idempotency-Key: ord-7f2c-2025-04-19
Authorization: Bearer <jwt>

{
  "customer_id": "u_1",
  "restaurant_id": "r_1",
  "items": [
    { "menu_item_id": "m_9", "quantity": 2 }
  ],
  "delivery_address_id": "a_77",
  "payment_method_id": "pm_card_99",
  "promo_code": null,
  "instructions": "Extra sauce please"
}

201 Created
{
  "data": {
    "id": "ord_abc",
    "status": "PLACED",
    "currency": "INR",
    "price_breakdown": {
      "subtotal": "360.00",
      "tax": "18.00",
      "delivery_fee": "30.00",
      "discount": "0.00",
      "surge": "0.00",
      "total": "408.00"
    },
    "estimated_delivery_at": "2025-04-19T20:30:00Z",
    "created_at": "2025-04-19T19:43:11Z"
  }
}
```

#### Errors

| HTTP | Code | When |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT` | bad payload |
| 404 | `NOT_FOUND` | restaurant or item missing |
| 409 | `ITEM_OUT_OF_STOCK` | inventory race |
| 409 | `IDEMPOTENCY_PAYLOAD_MISMATCH` | same key, different body |
| 422 | `RESTAURANT_CLOSED` | outside hours |
| 422 | `MIN_ORDER_NOT_MET` | below min order |
| 402 | `PAYMENT_DECLINED` | gateway declined |
| 503 | `NO_DRIVERS_NEARBY` | dispatch couldn't queue (rare; usually 201 + later cancel) |

### Get order

```
GET /v1/orders/{id}
200 OK
{ "data": { "id": "ord_abc", "status": "OUT_FOR_DELIVERY", "driver": {...}, ... } }
```

### List my orders

```
GET /v1/orders?status=PAST&cursor=...&limit=20
```

### Cancel order

```
POST /v1/orders/{id}:cancel
{ "reason": "Changed mind" }

200 OK { "data": { "id": "ord_abc", "status": "CANCELLED", "refund_status": "QUEUED" } }
```

Errors: `409 CANNOT_CANCEL_IN_STATE` if past `PREPARING`.

### Track order (WebSocket / SSE)

```
GET /v1/orders/{id}/track   (Upgrade: websocket)
```

Stream of:

```json
{ "type": "STATUS",   "status": "OUT_FOR_DELIVERY", "at": "..." }
{ "type": "DRIVER_LOC","lat": 12.97, "lng": 77.59, "at": "..." }
{ "type": "ETA",      "eta_minutes": 12 }
```

Polling fallback: `GET /v1/orders/{id}` every 10 s.

---

## Restaurant-facing endpoints

### List active orders

```
GET /v1/restaurants/{id}/orders?status=ACTIVE
```

### Accept / Reject

```
POST /v1/orders/{id}:accept   { "prep_minutes": 25 }
POST /v1/orders/{id}:reject   { "reason": "Out of stock", "items": ["m_9"] }
```

### Mark ready for pickup

```
POST /v1/orders/{id}:ready-for-pickup
```

### Update menu item availability

```
PATCH /v1/menu-items/{id}
{ "available": false }
```

---

## Driver-facing endpoints

### Online / offline

```
POST /v1/drivers/me:online    { "lat": ..., "lng": ... }
POST /v1/drivers/me:offline
```

### Push driver location

Drivers post location every 4 s via lightweight HTTP/2 or gRPC streaming.

```
POST /v1/drivers/me/locations
{ "lat": 12.97, "lng": 77.59, "heading": 80, "speed_mps": 7.2, "ts": "..." }
204 No Content
```

### Receive offer (push, not REST)

A push notification or WebSocket message:

```json
{
  "type": "DELIVERY_OFFER",
  "assignment_id": "as_1",
  "order_id": "ord_abc",
  "pickup": { "name": "Bowl Cafe", "lat": ..., "lng": ... },
  "drop":   { "lat": ..., "lng": ... },
  "distance_km": 4.5,
  "estimated_earnings": "75.00",
  "expires_at": "2025-04-19T19:44:00Z"
}
```

### Accept / Reject offer

```
POST /v1/assignments/{id}:accept
POST /v1/assignments/{id}:reject  { "reason": "TOO_FAR" }
```

### Pickup / deliver

```
POST /v1/assignments/{id}:pickup
POST /v1/assignments/{id}:deliver  { "otp": "1234" }     -- proof-of-delivery OTP
```

---

## Admin / support endpoints (sketch)

```
GET   /v1/admin/orders?stuck=true
POST  /v1/admin/orders/{id}:reassign
POST  /v1/admin/orders/{id}:refund      { "amount": "100.00", "reason": "..." }
POST  /v1/admin/drivers/{id}:suspend
```

These need higher RBAC.

---

## Webhooks (external partners)

### Payment gateway → us

```
POST https://us.example.com/webhooks/payments
X-Signature: sha256=<hmac>

{ "event": "payment.captured", "payment_id": "pay_x", "amount": "408.00", "order_id": "ord_abc" }
```

We verify the signature, ACK 200, and process idempotently.

### Restaurant POS → us

If we integrate with restaurant POS, they push status updates over a similar webhook with HMAC signing.

---

## Cross-cutting headers

| Header | Purpose |
| --- | --- |
| `Authorization: Bearer <jwt>` | Auth |
| `Idempotency-Key` | Mutation safety |
| `X-Request-Id` | Tracing |
| `X-Client-Version` | Compatibility |
| `Accept-Language` | i18n |

`X-Request-Id` is generated by the gateway if missing and propagated through all services.

---

## Pagination

Cursor-based always. Cursor encodes `(created_at, id)` as base64 JSON. Servers MUST support `limit` ≤ 100.

---

## Rate limits

| Bucket | Limit |
| --- | --- |
| Per user (mutation) | 60/min |
| Per user (read) | 600/min |
| Per restaurant POS | 600/min |
| Per driver location push | 30/min (1 every 2s upper bound) |

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
Retry-After: 7
```

---

## Validation rules

- `quantity ≥ 1` per item.
- Item must belong to the restaurant.
- Address must belong to the customer.
- `delivery_fee + tax + surge - discount + subtotal == total` (server computes; client just shows).
- Promo, if applied, must be valid for `customer_id`, `restaurant_id`, today.
- Restaurant must be `ACTIVE` and within operating hours.

Validation errors enumerate every field:

```json
400 {
  "error": {
    "code": "INVALID_ARGUMENT",
    "fields": [
      { "field": "items[0].quantity", "code": "MIN", "message": "Must be ≥ 1" },
      { "field": "promo_code",        "code": "EXPIRED" }
    ]
  }
}
```

---

## SLA per endpoint

| Endpoint | p99 target |
| --- | --- |
| `GET /restaurants` | 100 ms |
| `GET /restaurants/{id}/menu` | 80 ms |
| `POST /orders` | 250 ms |
| `GET /orders/{id}` | 80 ms |
| `POST /orders/{id}:cancel` | 250 ms |
| Driver location post | 50 ms |
| Tracking WS message | 200 ms end-to-end |

These drive caching, async, and DB choices in earlier files.
