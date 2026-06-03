# 06 · Ride Booking — API Design

## Rider APIs

### Fare estimate (no commitment)

```
POST /v1/rides/estimate
{
  "pickup": { "lat": 12.97, "lng": 77.59 },
  "drop":   { "lat": 13.00, "lng": 77.65 },
  "type":   "STANDARD"
}

200 OK
{
  "data": {
    "estimate": {
      "min": "150.00", "max": "190.00", "currency": "INR",
      "base": "40.00", "per_km": "12.00", "per_minute": "1.50",
      "surge_factor": "1.4", "expires_at": "2025-04-19T19:50:00Z"
    },
    "eta_minutes": 4,
    "available": true
  }
}
```

The `expires_at` lets the client know how long the quote is valid (~60 s typical).

### Request a ride

```
POST /v1/rides
Idempotency-Key: ride-abc-2025-04-19-001

{
  "rider_id": "r_1",
  "pickup": {...},
  "drop":   {...},
  "type":   "STANDARD",
  "payment_method_id": "pm_1",
  "estimate_token": "<signed estimate from /estimate>",
  "notes": "I'm at gate 3"
}

201 Created
{
  "data": {
    "id": "ride_xyz",
    "status": "REQUESTED",
    "estimate": {...},
    "looking_for_driver": true
  }
}
```

`estimate_token` is a server-signed JWT/HMAC of the (estimate, surge, expiry). Stops clients from spoofing a low fare.

### Cancel ride

```
POST /v1/rides/{id}:cancel
{ "reason": "Changed my mind" }

200 OK
{
  "data": {
    "id": "ride_xyz",
    "status": "CANCELLED",
    "cancellation_fee": "30.00",
    "refunded_amount": "0.00"
  }
}
```

Errors: `409 CANNOT_CANCEL` if state is `IN_TRIP` or beyond.

### Track / Get

```
GET /v1/rides/{id}
WS  /v1/rides/{id}/track
```

Track WS message types:
```
{ "type": "STATUS",   "status": "ARRIVING" }
{ "type": "DRIVER",   "name": "Alice", "rating": 4.7, "vehicle": {...} }
{ "type": "DRIVER_LOC", "lat": ..., "lng": ..., "heading": 80 }
{ "type": "ETA",      "eta_minutes": 3 }
{ "type": "FARE_FINAL", "total": "182.00", "breakdown": {...} }
```

### Rate ride

```
POST /v1/rides/{id}:rate
{ "rating": 5, "tip": "20.00", "comment": "Smooth driver" }
```

Idempotent on `(ride_id, rater_id)` — one rating per ride.

### History

```
GET /v1/rides?cursor=...&limit=20
```

Cursor is `(requested_at, id)` based.

---

## Driver APIs

### Online / offline

```
POST /v1/drivers/me:online   { "lat": ..., "lng": ... }
POST /v1/drivers/me:offline
```

### Push location

```
POST /v1/drivers/me/locations
{ "lat": ..., "lng": ..., "heading": 80, "speed_mps": 7.2, "ts": "..." }
204
```

### Receive offer

Push notification or via WebSocket:
```
{
  "type": "RIDE_OFFER",
  "offer_id": "of_1",
  "ride_id": "ride_xyz",
  "pickup": {...},
  "drop":   {...},
  "type":   "STANDARD",
  "estimated_earnings": "150.00",
  "expires_at": "2025-04-19T19:51:00Z"
}
```

### Accept / Decline

```
POST /v1/offers/{id}:accept
POST /v1/offers/{id}:decline   { "reason": "TOO_FAR" }
```

### Trip state transitions

```
POST /v1/rides/{id}:arrived
POST /v1/rides/{id}:start
POST /v1/rides/{id}:end   { "actual_distance_km": 7.2, "actual_minutes": 18 }
```

### SOS

```
POST /v1/safety/sos
{ "ride_id": "ride_xyz", "type": "DRIVER_INITIATED" }
204
```

Triggers safety workflow: alerts to safety team, recording (where legal), live location share.

---

## Admin APIs

```
GET    /v1/admin/rides?stuck=true
POST   /v1/admin/rides/{id}:reassign
POST   /v1/admin/rides/{id}:refund    { "amount": "...", "reason": "..." }
POST   /v1/admin/drivers/{id}:suspend
GET    /v1/admin/surge?city=BLR
POST   /v1/admin/surge:override       { "zone": "tdr1y3", "factor": 1.0 }
```

---

## Webhook (payment)

```
POST /webhooks/payments
X-Signature: sha256=<hmac>

{ "event": "payment.captured", "payment_id": "...", "ride_id": "..." }
```

Idempotent processing keyed by payment_id.

---

## Errors (sample)

```
400 INVALID_ARGUMENT
401 UNAUTHENTICATED
403 PERMISSION_DENIED
404 NOT_FOUND
409 RIDE_ALREADY_ASSIGNED
409 CANNOT_CANCEL_IN_STATE
409 IDEMPOTENCY_PAYLOAD_MISMATCH
422 ESTIMATE_EXPIRED
422 PAYMENT_METHOD_INVALID
429 RESOURCE_EXHAUSTED
503 NO_DRIVERS_AVAILABLE
```

`NO_DRIVERS_AVAILABLE` is rare — usually we return 201 and notify async via WS that no driver was found.

---

## Rate limits

| Bucket | Limit |
| --- | --- |
| Rider mutations | 30/min |
| Driver location post | 30/min (1 every 2 s) |
| Estimate calls | 60/min |

---

## SLA targets

| Endpoint | p99 |
| --- | --- |
| `POST /rides/estimate` | 150 ms |
| `POST /rides` | 250 ms |
| `WS message` (driver loc → rider) | 500 ms end-to-end |
| `POST /rides/{id}:cancel` | 250 ms |
| Match latency (event-driven) | 5 s p95 |
