# 08 · Ride Booking — Sequence Diagrams

## 1. Fare estimate

```mermaid
sequenceDiagram
  participant RA as Rider App
  participant API as Gateway
  participant PR as PricingService
  participant SU as SurgeService
  participant MA as MapsService

  RA->>API: POST /rides/estimate
  API->>PR: estimate(input)
  PR->>MA: distance + ETA(pickup, drop)
  MA-->>PR: 7.2 km / 18 min
  PR->>SU: factor(geohash, type)
  SU-->>PR: 1.4
  PR-->>API: Estimate{min,max,surge,token}
  API-->>RA: 200 {estimate}
```

`token` is HMAC-signed by the server so the rider can't fake a low surge later.

---

## 2. Request ride (happy path)

```mermaid
sequenceDiagram
  autonumber
  participant RA as Rider App
  participant RS as RideService
  participant PA as PaymentService
  participant DB as Postgres
  participant OB as Outbox
  participant K as Kafka
  participant MS as MatchingService

  RA->>RS: POST /rides {est_token, idempKey}
  RS->>RS: validate token signature, check expiry
  RS->>PA: authorize(amount = est.max)
  PA-->>RS: authId
  RS->>DB: BEGIN
  RS->>DB: insert ride(REQUESTED, surgeFactor)
  RS->>DB: insert outbox(RideRequested)
  RS->>DB: COMMIT
  RS-->>RA: 201 {rideId, looking}
  Note over OB,K: outbox poller publishes
  OB->>K: RideRequested
  K->>MS: consume → match
```

We **authorize** at request time (not capture). Capture happens at trip end with the actual amount.

---

## 3. Match flow

```mermaid
sequenceDiagram
  participant MS as MatchingService
  participant RG as Redis Geo
  participant DR as DriverRepository
  participant SC as ScoringStrategy
  participant PUSH as PushService
  participant DA as Driver App

  MS->>RG: GEOSEARCH nearby pickup, type
  RG-->>MS: 20 candidate driver IDs
  MS->>DR: SELECT drivers WHERE id IN (...) AND status='IDLE'
  DR-->>MS: 12 idle drivers
  loop rank candidates
    MS->>SC: score(driver, ride)
  end
  MS->>DR: UPDATE driver SET status='OFFER_PENDING' WHERE id=? AND status='IDLE' AND version=?
  DR-->>MS: 1 row updated
  MS->>PUSH: send RIDE_OFFER, expires=15s
  PUSH->>DA: notification
  alt accept
    DA->>MS: POST /offers/{id}:accept
    MS->>DR: UPDATE driver SET status='EN_ROUTE_PICKUP' (CAS)
    MS->>RS: bind ride(MATCHED, driverId)
    RS->>K: publish RideMatched
  else expire/decline
    MS->>DR: UPDATE driver SET status='IDLE' (CAS)
    MS->>MS: try next candidate
  end
```

---

## 4. Trip lifecycle

```mermaid
sequenceDiagram
  participant DA as Driver App
  participant RA as Rider App
  participant RS as RideService
  participant TS as TrackingService
  participant K as Kafka

  DA->>RS: POST /rides/{id}:arrived
  RS->>RS: state ARRIVING -> ARRIVED
  RS->>K: RideArrived
  K->>TS: notify rider via WS
  TS->>RA: STATUS=ARRIVED, OTP

  Note right of RA: Rider boards, shares OTP
  DA->>RS: POST /rides/{id}:start { otp }
  RS->>RS: validate OTP, ARRIVED -> IN_TRIP
  RS->>K: RideStarted

  loop driving
    DA->>TS: location every 4s
    TS->>RA: WS DRIVER_LOC
  end

  DA->>RS: POST /rides/{id}:end {km, minutes}
  RS->>RS: IN_TRIP -> COMPLETED, finalFare via PricingService
  RS->>PA: capture(rideId, finalFare.total, idemKey)
  PA-->>RS: captured
  RS->>K: RideCompleted
```

---

## 5. Rider cancels mid-arrival

```mermaid
sequenceDiagram
  participant RA as Rider App
  participant RS as RideService
  participant DR as Driver
  participant PA as PaymentService

  RA->>RS: POST /rides/{id}:cancel {reason}
  RS->>RS: state ARRIVING -> CANCELLED
  RS->>RS: cancellationFee = policy.feeFor(ride, RIDER)
  RS->>PA: capture(amount = fee)   // partial capture of authorized amount
  PA-->>RS: captured
  RS->>DR: driver release (EN_ROUTE_PICKUP -> IDLE)
  RS->>K: RideCancelledByRider
  RS-->>RA: 200 {fee, refund=0}
```

Driver gets compensated for distance traveled (out of the fee, plus base subsidy).

---

## 6. SOS

```mermaid
sequenceDiagram
  participant DA as Driver App or RA as Rider App
  participant SAFE as SafetyService
  participant OPS as Ops Console
  participant POL as External (police via partner)

  DA->>SAFE: POST /safety/sos {ride_id, kind}
  SAFE->>SAFE: append SosEvent (encrypted)
  SAFE->>OPS: page on-call safety team (live location)
  alt confirmed emergency
    SAFE->>POL: forward via partner API (where authorized)
  end
  SAFE-->>DA: 204
```

Safety is fail-closed: if the safety service is down, we still record locally and try alternative channels.

---

## 7. No-show

```mermaid
sequenceDiagram
  participant DA as Driver App
  participant RS as RideService

  DA->>RS: POST /rides/{id}:arrived
  Note over RS: 5-min timer starts
  alt rider not boarded after 5 min
    DA->>RS: POST /rides/{id}:no-show
    RS->>RS: state ARRIVED -> NO_SHOW
    RS->>PA: capture(no-show fee)
    RS-->>DA: 200
  end
```

We don't auto-fire NO_SHOW — driver explicitly requests it after waiting; the timer just makes the button visible.

---

## What these reveal

- All state transitions follow a deterministic pattern: **read → guard → CAS → publish event**.
- Authorization at request, capture at end (partial on cancel) — standard for hold-style payments.
- Tracking is a separate fan-out concern; not in the request path.
- Safety integrates via events but with the highest priority queue.
