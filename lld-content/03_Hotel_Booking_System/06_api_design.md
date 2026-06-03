# 06 · Hotel Booking — API Design

## Guest APIs

### Search

```
GET /v1/hotels/search?city=BLR&check_in=2025-06-01&check_out=2025-06-05
                     &adults=2&children=0&rooms=1
                     &filters=amenities:wifi,pool;rating_min:4
                     &sort=price_asc&cursor=...&limit=20

200 OK
{
  "data": [
    {
      "id": "h_1",
      "name": "Grand Hotel",
      "city": "Bangalore",
      "rating": 4.5,
      "lowest_price": "5400.00",
      "currency": "INR",
      "available_room_types": ["DELUXE","SUITE"],
      "amenities": ["wifi","pool"],
      "thumbnail_url": "..."
    }
  ],
  "next_cursor": "..."
}
```

The `lowest_price` is computed from `min(per-night price × nights)` across room types with availability for the whole range.

### View hotel + availability

```
GET /v1/hotels/{id}?check_in=2025-06-01&check_out=2025-06-05&adults=2&rooms=1

200 OK
{
  "data": {
    "hotel": { ... },
    "room_types": [
      {
        "id": "rt_1",
        "name": "Deluxe",
        "max_occupancy": 2,
        "amenities": [...],
        "available_rooms": 5,
        "nightly_rates": [
          { "date": "2025-06-01", "price": "1400.00" },
          { "date": "2025-06-02", "price": "1400.00" },
          ...
        ],
        "total_price": "5600.00",
        "cancellation_policy": { "free_until_hours": 48, "fee_pct_if_late": 0.5 }
      }
    ]
  }
}
```

`available_rooms` is the **minimum** across all requested nights.

### Create booking

```
POST /v1/bookings
Idempotency-Key: bk-2025-04-19-abc

{
  "guest_id": "u_1",
  "hotel_id": "h_1",
  "room_type_id": "rt_1",
  "check_in": "2025-06-01",
  "check_out": "2025-06-05",
  "room_count": 1,
  "adult_count": 2,
  "child_count": 0,
  "price_token": "<HMAC-signed price snapshot from /hotels/{id}>",
  "payment_method_id": "pm_99",
  "promo_code": null
}

201 Created
{
  "data": {
    "id": "bk_xyz",
    "status": "CONFIRMED",
    "total_price": "5600.00",
    "currency": "INR",
    "price_breakdown": {...},
    "cancellation_policy": {...},
    "check_in_at": "2025-06-01T15:00:00Z",
    "check_out_by": "2025-06-05T11:00:00Z"
  }
}
```

Errors:

| HTTP | Code | When |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT` | bad fields |
| 404 | `NOT_FOUND` | hotel/room missing |
| 409 | `INVENTORY_UNAVAILABLE` | one or more nights without space |
| 409 | `IDEMPOTENCY_PAYLOAD_MISMATCH` | same key, different payload |
| 422 | `PRICE_TOKEN_EXPIRED` | snapshot too old, re-quote |
| 402 | `PAYMENT_DECLINED` | gateway declined |

### Get booking

```
GET /v1/bookings/{id}
```

### Modify booking

```
PATCH /v1/bookings/{id}
{
  "check_in": "2025-06-02",
  "check_out": "2025-06-06",
  "room_count": 1
}
```

Server computes delta inventory ops:
- Release nights {6-01}.
- Reserve nights {6-05}.
- Recompute price; charge or refund delta.

If the new dates are not available, returns 409 with the conflicting dates.

### Cancel booking

```
POST /v1/bookings/{id}:cancel
{ "reason": "Travel plans changed" }

200 OK
{
  "data": {
    "id": "bk_xyz",
    "status": "CANCELLED",
    "fee": "2800.00",
    "refunded_amount": "2800.00",
    "refund_eta_days": 5
  }
}
```

Errors: `409 CANNOT_CANCEL_AFTER_CHECK_IN`.

### Check in / out (often by hotel staff, not guest)

```
POST /v1/bookings/{id}:check-in
POST /v1/bookings/{id}:check-out
```

---

## Hotel admin APIs

### Bulk inventory update

```
PATCH /v1/hotels/{id}/inventory
{
  "room_type_id": "rt_1",
  "ranges": [
    {
      "from": "2025-06-01",
      "to": "2025-06-30",
      "total_rooms": 50,
      "base_price": "1400.00"
    },
    {
      "from": "2025-07-01",
      "to": "2025-07-15",
      "total_rooms": 50,
      "base_price": "1800.00"
    }
  ]
}

200 OK
```

### Block dates

```
POST /v1/hotels/{id}/inventory:block
{
  "room_type_id": "rt_1",
  "from": "2025-08-10",
  "to": "2025-08-12",
  "reason": "Maintenance"
}
```

If active bookings span the range → 409 with details. Admin can override with `force=true` and offer alternatives to affected guests.

### Manage policies

```
PUT /v1/hotels/{id}/cancellation-policies/{policy_id}
PATCH /v1/hotels/{id}/room-types/{rt_id}
```

---

## Search-related considerations

- Search responses are **eventually consistent**. The gold copy of inventory is Postgres; ES is the read store with ~30 s lag.
- The booking flow always re-validates against the source-of-truth.
- Search caches popular queries in Redis with TTL ~30 s.

---

## Pricing token (HMAC-signed)

When a search or hotel-detail returns prices, the server signs:

```json
{
  "hotel_id": "h_1",
  "room_type_id": "rt_1",
  "check_in": "2025-06-01",
  "check_out": "2025-06-05",
  "room_count": 1,
  "total_price": "5600.00",
  "price_breakdown": {...},
  "exp": 1714131000
}
```

Booking POST passes this token. Server verifies HMAC + expiry. If expired (~5 min TTL), reject with `PRICE_TOKEN_EXPIRED`.

This stops:
- Stale price reuse.
- Client-side price tampering.
- Inconsistency between view and book.

---

## Webhooks

```
POST /webhooks/payments    (payment status updates)
POST /webhooks/pms         (hotels with PMS push availability changes)
```

Both HMAC-signed. Idempotent processing.

---

## Errors taxonomy

```
INVALID_ARGUMENT
NOT_FOUND
INVENTORY_UNAVAILABLE
PRICE_TOKEN_EXPIRED
PRICE_MISMATCH
CANNOT_MODIFY_AFTER_CHECK_IN
CANNOT_CANCEL_AFTER_CHECK_IN
PAYMENT_DECLINED
RESOURCE_EXHAUSTED   // rate limit
INTERNAL
```

---

## SLA

| Endpoint | p99 |
| --- | --- |
| Search | 300 ms |
| Hotel detail (cache hit) | 100 ms |
| Booking POST | 500 ms |
| Cancel | 300 ms |
| Modify | 600 ms |
