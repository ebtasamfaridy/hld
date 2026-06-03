# 08 · BookMyShow — Sequence Diagrams

## 1. Hold flow (happy path)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant API as BookingService
    participant SL as SeatLock (Redis)
    participant SR as ShowRepository
    participant PP as PricingPolicy
    participant HR as HoldRepository
    participant K as EventBus

    U->>API: POST /shows/s1/holds {seats: [A1,A2]}
    API->>SR: getShow(s1)
    SR-->>API: Show{...}
    API->>SL: tryHold(s1, A1, u42, 600s)
    SL-->>API: true
    API->>SL: tryHold(s1, A2, u42, 600s)
    SL-->>API: true
    API->>PP: quote(show, [A1,A2], now)
    PP-->>API: PriceQuote(₹750)
    API->>HR: save Hold(HELD, expires=now+10m, quote=₹750)
    API->>K: publish HoldCreated
    API-->>U: 201 Hold(holdId, expires_at, ₹750)
```

## 2. Hold conflict (one of the seats taken)

```mermaid
sequenceDiagram
    autonumber
    participant API as BookingService
    participant SL as SeatLock

    API->>SL: tryHold(s1, A1) → true
    API->>SL: tryHold(s1, A2) → FALSE (already held)
    Note over API: rollback the A1 hold to keep all-or-nothing
    API->>SL: release(s1, A1, u42)
    API-->>U: 409 conflict {seats: [A2]}
```

## 3. Confirm (happy path)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant API as BookingService
    participant HR as HoldRepository
    participant Pay as PaymentService
    participant DB as Postgres TX
    participant SL as SeatLock
    participant K as EventBus

    U->>API: POST /holds/h123/confirm {token, idem}
    API->>HR: load h123 (must be HELD, expires>now)
    HR-->>API: Hold
    API->>Pay: charge(₹750, token, idem)
    Pay-->>API: PaymentResult.Success(ref)

    API->>DB: BEGIN
    DB->>DB: INSERT booking
    DB->>DB: INSERT booking_seats (PK guards)
    DB->>DB: UPDATE hold status=CONFIRMED
    DB->>DB: COMMIT
    API->>SL: DEL hold:s1:A1, hold:s1:A2  (locks no longer needed)
    API->>K: publish BookingConfirmed
    API-->>U: 200 Booking(id, ticket_qr)
```

## 4. Hold expires before payment lands

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant API as BookingService
    participant HR as HoldRepository
    participant Pay as PaymentService

    U->>API: POST /holds/h123/confirm {token, idem}
    API->>HR: load h123
    HR-->>API: Hold (expired!)
    API->>API: status=EXPIRED, do NOT charge
    API-->>U: 410 Hold expired
```

If we already charged the user before checking, refund:

```mermaid
sequenceDiagram
    autonumber
    participant API as BookingService
    participant HR as HoldRepository
    participant Pay as PaymentService

    API->>Pay: charge → Success
    API->>HR: load h123 → expired
    API->>Pay: refund(ref, idem)
    API-->>U: 410 + refund initiated
```

The order in the implementation matters: **load + lock-check before charge**.

## 5. PK guard catches Redis miss

```mermaid
sequenceDiagram
    autonumber
    participant API as BookingService
    participant DB as Postgres TX

    Note over API: Redis hold somehow let two users through (e.g., split-brain)
    API->>DB: INSERT booking_seats (s1, A1, b-1) ✓
    par Other transaction
        API->>DB: INSERT booking_seats (s1, A1, b-2) → unique-violation
    end
    DB--xAPI: 23505 error
    API->>API: rollback
    API->>API: refund payment
    API-->>U: 409 seat-conflict
```

## 6. Show cancellation (admin)

```mermaid
sequenceDiagram
    autonumber
    participant Op as Admin
    participant API as Admin API
    participant DB as Postgres
    participant K as EventBus

    Op->>API: POST /admin/shows/s1/cancel
    API->>DB: UPDATE shows status=CANCELLED
    API->>DB: SELECT bookings WHERE show=s1 AND status=CONFIRMED
    API->>DB: UPDATE bookings status=CANCELLED (bulk)
    API->>K: publish ShowCancelled, BookingCancelled[]
    Note over Pay: refund worker consumes events and processes refunds
```

## Output

```
Hold:        SETNX per seat with TTL; quote; persist Hold row
Confirm:     load → charge → Postgres TX (insert booking + seats + update hold) → DEL Redis → publish
Expired:     reject before charge; if charged, refund
Defense:     PK on (show, seat) catches any leak
Cancel:      bulk admin path; downstream consumers handle refunds
```
