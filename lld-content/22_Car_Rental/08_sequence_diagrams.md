# 08 · Car Rental — Sequence Diagrams

## 1. Search

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant API
    participant SS as SearchSvc
    participant ES as Elasticsearch
    participant Geo as Redis GEO

    U->>API: GET /search?lat=...&startAt=...&endAt=...
    API->>SS: search
    SS->>ES: query (city + window predicate)
    ES-->>SS: vehicleIds
    SS->>Geo: GEOSEARCH (sort by distance)
    Geo-->>SS: ordered vehicle list
    SS-->>API: results + estimatedFare
    API-->>U: 200 results
```

The ES index is refreshed via CDC from `timeslots` every 30 s. Search is intentionally eventually consistent — the place-reservation endpoint re-validates strictly.

---

## 2. Place reservation — happy path

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant API
    participant RS as ReservationSvc
    participant Inv as SlotInventory
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant DB
    participant K as Kafka

    U->>API: POST /reservations {vehicleId, window, idemKey}
    API->>RS: place

    RS->>DB: SELECT reservations WHERE user_id=u AND idemKey=k
    alt already exists
        RS-->>API: 200 cached reservation
    end

    RS->>RS: validate KYC + vehicle active

    RS->>Inv: reserve(vehicleId, window, resvId)
    Inv->>DB: BEGIN
    Inv->>DB: INSERT timeslots [N rows] ON CONFLICT DO NOTHING
    Inv->>DB: SELECT count WHERE reservation_id=resvId
    alt count < N
        Inv->>DB: ROLLBACK
        Inv-->>RS: Conflict(blockedBuckets)
        RS-->>API: 409 OUT_OF_STOCK
    end
    Inv->>DB: COMMIT
    Inv-->>RS: Reserved

    RS->>Pay: authorize(deposit + buffer, idemKey)
    Pay->>GW: AUTH
    GW-->>Pay: authId
    Pay-->>RS: AUTHORIZED

    RS->>DB: BEGIN
    RS->>DB: INSERT reservation (status=CONFIRMED)
    RS->>DB: INSERT payment (status=AUTHORIZED)
    RS->>DB: INSERT outbox(ReservationCreated)
    RS->>DB: COMMIT

    Note over DB,K: Outbox → Kafka publishes ReservationCreated
    K-->>U: (later) confirmation email

    RS-->>API: 201 reservation
    API-->>U: 201
```

---

## 3. Place reservation — slot conflict (rollback)

```mermaid
sequenceDiagram
    autonumber
    participant RS as ReservationSvc
    participant Inv as SlotInventory
    participant DB

    RS->>Inv: reserve(vehicleId, window, resvId)
    Inv->>DB: BEGIN
    Inv->>DB: INSERT timeslots [N rows] ON CONFLICT DO NOTHING
    DB-->>Inv: 47 rows inserted (expected 60)
    Inv->>DB: ROLLBACK
    Inv-->>RS: Conflict(blockedBuckets=[h13..h25])

    Note over RS: No payment auth attempted, no rows persisted — clean rollback
    RS-->>RS: respond 409 OUT_OF_STOCK
```

---

## 4. Place reservation — payment declined

```mermaid
sequenceDiagram
    autonumber
    participant RS as ReservationSvc
    participant Inv as SlotInventory
    participant Pay as PaymentSvc
    participant GW as Gateway

    RS->>Inv: reserve(...)
    Inv-->>RS: Reserved

    RS->>Pay: authorize(deposit, idemKey)
    Pay->>GW: AUTH
    GW-->>Pay: DECLINED (INSUFFICIENT_FUNDS)
    Pay-->>RS: PAYMENT_DECLINED

    RS->>Inv: release(resvId)
    Inv->>Inv: DELETE timeslots WHERE reservation_id=resvId

    RS-->>RS: respond 402 PAYMENT_DECLINED
```

---

## 5. Pickup (unlock) — happy path

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant API
    participant TS as TripSvc
    participant DB
    participant IoT as IoT Adapter
    participant Veh as Vehicle modem
    participant K as Kafka

    U->>API: POST /trips/start {reservationId, gps, odoStart, fuelStart, prePhotos}
    API->>TS: start

    TS->>DB: SELECT reservation, vehicle
    TS->>TS: validate status=CONFIRMED
    TS->>TS: validate now in [startAt-15m, startAt+30m]
    TS->>TS: distance(gps, vehicle.location) < 50m

    alt out of fence
        TS-->>API: 422 GPS_OUT_OF_FENCE
    end

    TS->>IoT: unlock(vehicleId, idemToken)
    IoT->>Veh: UNLOCK command
    Veh-->>IoT: ACK
    IoT-->>TS: UNLOCKED

    TS->>DB: BEGIN
    TS->>DB: INSERT trip (status=PICKED_UP)
    TS->>DB: UPDATE reservation status=ACTIVE
    TS->>DB: INSERT trip_photos (PRE)
    TS->>DB: INSERT outbox(TripStarted)
    TS->>DB: COMMIT

    TS-->>API: 200 {tripId, doorState=UNLOCKED}
```

---

## 6. Pickup — IoT timeout

```mermaid
sequenceDiagram
    autonumber
    participant TS as TripSvc
    participant IoT as IoT Adapter
    participant Veh as Vehicle modem

    TS->>IoT: unlock(vehicleId, idemToken)
    IoT->>Veh: UNLOCK
    Veh--xIoT: timeout 5s
    IoT->>Veh: UNLOCK retry (same idemToken)
    Veh--xIoT: timeout 5s
    IoT-->>TS: IOT_UNREACHABLE

    Note over TS: Trip not created, reservation stays CONFIRMED
    TS-->>TS: respond 503 + ops alert
    Note over TS: Ops can manually unlock and force-start the trip
```

---

## 7. Return — fare compute and capture

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant API
    participant TS as TripSvc
    participant Pricing
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant DB
    participant K as Kafka

    U->>API: POST /trips/{id}/end {gps, odoEnd, fuelEnd, postPhotos}
    API->>TS: end

    TS->>TS: validate gps in dropZone
    TS->>TS: validate ≥4 post photos uploaded

    TS->>DB: SELECT trip + reservation
    TS->>Pricing: compute(reservation, trip)
    Pricing-->>TS: lines + total

    alt total <= deposit
        TS->>Pay: capture(authId, total)
        Pay->>GW: CAPTURE total
        GW-->>Pay: captureId
        Note over Pay: void any unused auth amount
    else total > deposit
        TS->>Pay: capture(authId, deposit)
        TS->>Pay: mit(savedMethod, total - deposit)
        Pay->>GW: MIT charge
        GW-->>Pay: charge ok
    end

    TS->>DB: BEGIN
    TS->>DB: UPDATE trip status=RETURNED, final_fare=total
    TS->>DB: UPDATE reservation status=COMPLETED
    TS->>DB: INSERT trip_photos (POST)
    TS->>DB: INSERT outbox(TripCompleted)
    TS->>DB: COMMIT

    TS-->>API: 200 {fareBreakdown}
```

---

## 8. Cancellation

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant API
    participant RS as ReservationSvc
    participant Pay as PaymentSvc
    participant Inv as SlotInventory
    participant K as Kafka

    U->>API: POST /reservations/{id}/cancel {idemKey}
    API->>RS: cancel

    RS->>RS: load reservation
    alt status not in (HELD, CONFIRMED)
        RS-->>API: 409 ALREADY_ACTIVE
    end

    RS->>RS: policy.refundFor(reservation, now) → refundAmount

    RS->>Pay: voidAuth(authId)
    Note over Pay: deposit released as per refund tier

    RS->>Inv: release(reservationId)
    Inv->>Inv: DELETE timeslots WHERE reservation_id=resvId

    RS->>RS: status=CANCELLED, refunded=refundAmount
    RS-->>K: outbox(ReservationCancelled)
    RS-->>API: 200 {refundMinor}
```

---

## 9. Damage claim — async charge

```mermaid
sequenceDiagram
    autonumber
    participant Ops
    participant DC as DamageClaimSvc
    participant Reviewer
    participant Pay as PaymentSvc
    participant GW as Gateway
    participant DB
    participant K as Kafka

    Ops->>DC: POST /damage-claims {tripId, severity, estimate, photos}
    DC->>DB: INSERT claim (status=REPORTED)
    DC-->>K: outbox(DamageClaimReported)

    Reviewer->>DC: POST /decide {APPROVED, amount}
    DC->>DB: UPDATE claim status=APPROVED, decided_at, reviewer_id

    DC-->>K: outbox(DamageClaimApproved)

    Note over K,Pay: Async — Payment Service consumes
    K->>Pay: DamageClaimApproved
    Pay->>GW: MIT charge(savedMethod, amount, idemKey=claimId)
    alt success
        GW-->>Pay: charge ok
        Pay->>DB: INSERT damage_charge (CHARGED)
    else declined
        GW-->>Pay: declined
        Pay->>DB: INSERT damage_charge (DUNNING)
        Note over Pay: User flagged DUNNING — new bookings blocked
    end
```

The ride end stays fast because damage assessment is async. Days later, the saved payment instrument is charged via MIT — legally backed by consent recorded at booking.

---

## 10. No-show sweep

```mermaid
sequenceDiagram
    participant Cron
    participant DB
    participant Pay as PaymentSvc
    participant Inv as SlotInventory
    participant K as Kafka

    Note over Cron: every 60s
    Cron->>DB: SELECT reservations WHERE status='CONFIRMED' AND start_at < now() - 30min AND no trip exists
    loop per stale reservation
        Cron->>DB: UPDATE reservation status=NO_SHOW
        Cron->>Inv: release(reservationId)
        Cron->>Pay: voidAuth (or capture forfeit per policy)
        Cron-->>K: outbox(NoShowDetected)
    end
```

---

## 11. Reservation drift — late return

```mermaid
sequenceDiagram
    autonumber
    participant U as Renter
    participant TS as TripSvc
    participant Pricing
    participant Inv as SlotInventory

    Note over U: Trip booked end_at=09:00 — renter ends at 11:30 (2.5 hr late)

    U->>TS: end trip at 11:30
    TS->>Pricing: compute(reservation, trip)
    Pricing->>Pricing: base + per-km + late_fee(2.5hr × 2× hourly)
    Pricing-->>TS: total with late fee

    Note over TS: Slots beyond original end were never reserved
    TS->>Inv: extend(vehicleId, [09:00..12:00], reservationId) - best effort
    alt next booking conflicts
        Inv-->>TS: Conflict — but trip already ended, just bill late fee
    end
```

The "next renter is at 12:30" scenario triggers an ops escalation if conflict was detected mid-trip.

---

## What these reveal

- **Place reservation** is the core saga: idempotency lookup, atomic slot insert, payment auth, persist + outbox.
- **Pickup** validates GPS + window before anything happens to the vehicle. Failures don't create a Trip.
- **Return** computes fare in two stages (deposit capture, then MIT for any difference). User sees full breakdown.
- **Cancellation** voids auth + deletes slot rows in one go.
- **Damage claims** are decoupled from the trip-end hot path; they charge later via MIT.
- **No-show** is a clock-driven cron; not user-triggered.
