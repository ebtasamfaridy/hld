# 06 · Car Rental — API Design

## Conventions

- REST + JSON over HTTPS.
- All write endpoints accept `Idempotency-Key` (24 h dedup window).
- Versioned: `/v1/...`.
- RFC 7807 problem+json for errors.
- Pagination via `cursor` + `limit`.

---

## Search & catalog

### Search vehicles

```
GET /v1/search?lat=12.93&lng=77.62&startAt=2026-04-29T19:00&endAt=2026-05-02T09:00&class=SUV
→ 200
{
  "results": [
    {
      "vehicleId": "...",
      "model": { "id": "...", "name": "Hyundai Creta", "seats": 5 },
      "plate": "KA01AB1234",
      "fuelLastSeenPct": 80,
      "distanceMetres": 412,
      "estimatedFare": { "minor": 1572000, "currency": "INR" }
    }
  ],
  "nextCursor": "eyJ..."
}
```

### Vehicle detail

```
GET /v1/vehicles/{vehicleId}
→ 200
{
  "vehicleId": "...",
  "model": {...},
  "photos": ["https://cdn/..."],
  "fuelLastSeenPct": 80,
  "lastOdoKm": 45120,
  "currentLocation": { "lat": ..., "lng": ... },
  "lastInspection": "2026-04-25"
}
```

---

## Reservation

### Place reservation

```
POST /v1/reservations
Idempotency-Key: <uuid>
{
  "vehicleId": "...",
  "startAt": "2026-04-29T19:00:00Z",
  "endAt":   "2026-05-02T09:00:00Z",
  "paymentMethodId": "...",
  "promoCode": "WEEKEND10"
}

→ 201 (success)
{
  "reservationId": "...",
  "status": "CONFIRMED",
  "baseFare": { "minor": 1080000, "currency": "INR" },
  "deposit":  { "minor": 200000,  "currency": "INR" },
  "expiresAt": "2026-04-29T18:30:00Z"
}

→ 409 OUT_OF_STOCK   { "blockedSlots": [...] }
→ 402 PAYMENT_FAILED { "reason": "INSUFFICIENT_FUNDS" }
→ 403 KYC_PENDING
→ 422 INVALID_WINDOW   (e.g., end before start, more than 30 days)
→ 409 IDEMPOTENCY_CONFLICT (same key, different payload)
```

### Cancel reservation

```
POST /v1/reservations/{id}/cancel
Idempotency-Key: <uuid>
{ "reason": "PLAN_CHANGED" }

→ 200
{ "status": "CANCELLED", "refundMinor": 540000, "policy": "TIER_50" }

→ 409 ALREADY_ACTIVE   (can't cancel after pickup)
→ 410 ALREADY_CANCELLED
```

### List my reservations

```
GET /v1/reservations?status=CONFIRMED,ACTIVE&cursor=&limit=20
→ 200 [reservations...]
```

### Extend (mid-trip)

```
POST /v1/reservations/{id}/extend
Idempotency-Key: <uuid>
{ "newEndAt": "2026-05-02T15:00:00Z" }

→ 200 { "status":"EXTENDED", "additionalFare": {...} }
→ 409 SLOTS_UNAVAILABLE { "conflictAt": "..." }
```

---

## Trip lifecycle

### Pickup (unlock)

```
POST /v1/trips/start
Idempotency-Key: <uuid>
{
  "reservationId": "...",
  "gps": { "lat": 12.92, "lng": 77.63, "accuracyMetres": 8 },
  "odoStartKm": 45120,
  "fuelStartPct": 80,
  "prePhotos": ["s3://uploads/abc1", "s3://uploads/abc2", ...]
}

→ 200 { "tripId": "...", "doorState": "UNLOCKED" }

→ 409 RESERVATION_NOT_READY   (status != CONFIRMED)
→ 409 OUTSIDE_PICKUP_WINDOW   ("expectedAt": "...")
→ 422 GPS_OUT_OF_FENCE        ("distanceMetres": 312)
→ 503 IOT_UNREACHABLE         (try again, then ops)
```

### Trip GPS ping (high-volume)

```
POST /v1/trips/{id}/ping
{ "gps": {...}, "ts": "...", "speedKph": 60 }
→ 202 (accepted, async)
```

### Lock / Unlock during trip

```
POST /v1/trips/{id}/lock
POST /v1/trips/{id}/unlock
→ 200 { "doorState": "LOCKED" | "UNLOCKED" }
```

### Return

```
POST /v1/trips/{id}/end
Idempotency-Key: <uuid>
{
  "gps": {...},
  "odoEndKm": 45840,
  "fuelEndPct": 25,
  "postPhotos": [...]
}

→ 200
{
  "tripId": "...",
  "status": "RETURNED",
  "fareBreakdown": {
    "base": { "minor": 1080000 },
    "perKm": { "minor": 432000, "kmDriven": 720, "rateMinor": 600 },
    "fuel":  { "minor": 60000, "fuelLitresUsed": 6 },
    "lateFee": { "minor": 0 },
    "total":   { "minor": 1572000, "currency": "INR" }
  },
  "captureStatus": "CAPTURED"
}

→ 422 GPS_OUT_OF_FENCE
→ 422 PHOTOS_INSUFFICIENT { "expected": 4, "got": 3 }
```

---

## Damage claims

### Report claim (ops only)

```
POST /v1/damage-claims
Authorization: Ops/Reviewer
Idempotency-Key: <uuid>
{
  "tripId": "...",
  "severity": "MEDIUM",
  "estimateMinor": 350000,
  "notes": "Rear bumper dent, ~2 inches",
  "photos": ["s3://claims/..."]
}
→ 201 { "claimId": "...", "status": "REPORTED" }
```

### Approve / reject (reviewer)

```
POST /v1/damage-claims/{id}/decide
{ "decision": "APPROVED", "approvedAmountMinor": 350000 }
→ 200 { "status": "APPROVED" }

POST /v1/damage-claims/{id}/decide
{ "decision": "REJECTED", "reason": "Pre-existing damage in pre-photos" }
→ 200 { "status": "REJECTED" }
```

Approval triggers the MIT charge asynchronously (consumed from Kafka by Payment Service).

### Renter dispute

```
POST /v1/damage-claims/{id}/dispute
{ "reason": "I returned it clean" }
→ 200 { "status": "DISPUTED" }
```

---

## Webhooks

### Payment gateway

```
POST /webhooks/payments
X-Signature: hmac-sha256
{ "eventId": "evt_...", "type": "payment.captured", "paymentRef": "...", "amountMinor": 1572000 }
→ 200 (always; we dedupe by eventId)
```

### IoT (vehicle telemetry)

```
POST /webhooks/iot/heartbeat
{ "vehicleId": "...", "ts": "...", "lat": ..., "lng": ..., "fuelPct": 80, "odoKm": 45120, "doorState": "LOCKED" }
→ 202
```

---

## Operator API

### Vehicle CRUD

```
POST /v1/operator/vehicles    { model, plate, vin, cityId, ... } → 201
PATCH /v1/operator/vehicles/{id} { status: "MAINTENANCE", reason } → 200
DELETE /v1/operator/vehicles/{id} → 204 (soft retire)
```

### Pricing config

```
PATCH /v1/operator/pricing/{cityId}/{modelId}
{ "hourlyRateMinor": 25000, "perKmRateMinor": 600 }
→ 200
```

### Manual override (lost token, stuck barrier)

```
POST /v1/operator/trips/{id}/force-end
{ "reason": "renter app crashed", "fareMinor": 1500000 }
→ 200
```

---

## Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| `OUT_OF_STOCK` | 409 | Slot conflict at place-reservation |
| `IDEMPOTENCY_CONFLICT` | 409 | Same key, different payload |
| `KYC_PENDING` | 403 | License/KYC not approved yet |
| `OUTSIDE_PICKUP_WINDOW` | 409 | Now < startAt − 15 min or > startAt + 30 min |
| `GPS_OUT_OF_FENCE` | 422 | Distance exceeds 50 m |
| `IOT_UNREACHABLE` | 503 | Vehicle modem timeout |
| `PHOTOS_INSUFFICIENT` | 422 | Less than 4 pre/post photos |
| `RESERVATION_NOT_READY` | 409 | Status not CONFIRMED at unlock attempt |
| `SLOTS_UNAVAILABLE` | 409 | Mid-trip extension slot conflict |
| `PAYMENT_DECLINED` | 402 | Gateway rejected |
| `DUNNING` | 423 | User has unpaid charges; new bookings blocked |

---

## Idempotency rules

- Reservation create / cancel / extend → `UNIQUE(user_id, idempotency_key)`.
- Trip start / end → idempotency-key tied to reservation_id.
- Payment authorize / capture / refund → gateway idempotency key = our internal id.
- Damage MIT charge → idempotency key = claim_id.
- Webhooks → `processed_events(eventId)`.

---

## Output

```
Browse:     GET search, GET vehicle detail (cached, eventually consistent)
Reserve:    POST /reservations (idempotent, atomic slot insert)
Trip:       POST start / end (with GPS validation), PATCH lock/unlock, POST ping
Damage:     POST claim, POST decide, POST dispute (ops + reviewer scopes)
Webhooks:   payment + IoT, both eventId-deduped
Operator:   vehicle CRUD, pricing config, manual override
```
