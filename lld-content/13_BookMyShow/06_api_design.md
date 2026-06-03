# 06 · BookMyShow — API Design

## Catalog (read-heavy, cacheable)

```http
GET /v1/cities                                          # CDN: 1h
GET /v1/cities/{city}/movies                            # CDN: 30m
GET /v1/movies/{id}                                     # CDN: 1h
GET /v1/movies/{id}/shows?city=&date=                   # CDN: 5m
GET /v1/shows/{id}                                      # CDN: 30s
GET /v1/shows/{id}/seats                                # 5s; cache-keyed by version
```

## Booking flow

```http
POST /v1/shows/{id}/holds                               # Idempotency-Key
{ "seat_ids": ["A1","A2"], "user_id": "u-42" }
→ 201
{
  "hold_id": "h-123",
  "expires_at": "2026-04-29T17:42:00Z",
  "amount_minor": 75000,
  "breakdown": {
    "seats": [{"seat":"A1","cat":"PREMIUM","price":350},
              {"seat":"A2","cat":"PREMIUM","price":350}],
    "convenience_fee": 50,
    "surge_multiplier": 1.0
  }
}
→ 409 (one or more seats just got held by someone else)
```

```http
POST /v1/holds/{id}/confirm                             # Idempotency-Key
{ "payment_method": "CARD", "payment_token": "..." }
→ 200
{ "booking_id": "b-456", "status": "CONFIRMED",
  "ticket_qr": "data:image/png;base64,..." }
→ 410 (hold expired)
→ 402 (payment failed)
```

```http
DELETE /v1/holds/{id}
→ 204    # explicit cancel; releases seats
```

## My bookings

```http
GET  /v1/me/bookings?status=upcoming
GET  /v1/me/bookings/{id}
POST /v1/bookings/{id}/cancel       # Idempotency-Key
```

## Operator

```http
POST /v1/admin/shows/{id}/cancel     # show cancellation; bulk refund
GET  /v1/admin/shows/{id}/occupancy
```

## Webhooks (V2)

```http
POST <gateway>/payments/webhooks
{ "event": "payment.captured", "ref": "...", "booking_id": "b-456" }
```

We treat gateway webhooks as **secondary truth**; the gateway response on confirm is **primary**. Webhooks are reconciliation.

## Idempotency

- `Idempotency-Key` mandatory on hold-creation, hold-confirm, booking-cancel.
- Server stores key + response for 24 h.
- Repeat with same key → original response.

## Errors

```jsonc
HTTP/1.1 409
{
  "type": ".../seat-conflict",
  "title": "Some of the requested seats are no longer available",
  "conflicting_seats": ["A1"]
}

HTTP/1.1 410
{
  "type": ".../hold-expired",
  "title": "Hold expired before payment was completed",
  "hold_id": "h-123"
}
```

## Rate limits

- Per-IP: 60 req/min on browse, 10 holds/min, 3 confirms/min.
- Per-user: 5 holds/min, 5 confirms/min.
- Endpoint protection from scrapers.

## Output

```
Catalog:   GET endpoints (CDN-cached)
Booking:   POST /shows/{id}/holds → POST /holds/{id}/confirm
My:        /me/bookings; cancel
Admin:     show cancel, occupancy
Idempotent: hold-create, hold-confirm, booking-cancel
```
