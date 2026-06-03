# 08 · Parking Lot — Sequence Diagrams

## 1. Entry — happy path

```mermaid
sequenceDiagram
    autonumber
    participant V as Vehicle
    participant EG as EntryGate
    participant PL as ParkingLot
    participant AS as AllocationStrategy
    participant SR as SpotRepository
    participant TR as TicketRepository

    V->>EG: pull up (plate, type)
    EG->>PL: requestEntry(plate, type)
    PL->>AS: allocate(lot, vehicle)
    loop iterate spots in strategy order
        AS->>SR: candidate Spot
        AS->>SR: spot.tryClaim(newTicketId)
        alt CAS succeeded
            AS-->>PL: Optional.of(spot)
        else lost CAS
            Note over AS: try next candidate
        end
    end
    PL->>TR: save Ticket(plate, spotId, now, ACTIVE)
    PL-->>EG: Admitted(ticketId, spotId)
    EG-->>V: print ticket and raise barrier
```

## 2. Entry — lot full for type

```mermaid
sequenceDiagram
    autonumber
    participant EG as EntryGate
    participant PL as ParkingLot
    participant AS as AllocationStrategy

    EG->>PL: requestEntry(plate, TRUCK)
    PL->>AS: allocate(lot, truck)
    AS-->>PL: Optional.empty()
    PL-->>EG: LotFull(TRUCK)
    EG->>EG: display "No LARGE spots, please go to lot B"
```

## 3. Exit — happy path

```mermaid
sequenceDiagram
    autonumber
    participant V as Vehicle
    participant XG as ExitGate
    participant PL as ParkingLot
    participant TR as TicketRepository
    participant PS as PricingStrategy
    participant Pay as PaymentService
    participant SR as SpotRepository

    V->>XG: scan ticket
    XG->>PL: quote(ticketId, now)
    PL->>TR: load ticket (must be ACTIVE)
    PL->>PS: compute(ticket, now, spotType)
    PS-->>PL: ₹150
    PL-->>XG: PreExitQuote(₹150)
    XG-->>V: show ₹150
    V->>XG: tap card
    XG->>Pay: charge(₹150, token)
    Pay-->>XG: Success
    XG->>PL: settle(ticketId, paymentResult)
    PL->>TR: ticket.close(now, ₹150, paymentRef)
    PL->>SR: spot.release(ticketId)
    PL-->>XG: ExitResult.Closed
    XG-->>V: open barrier
```

## 4. Concurrent claim (two gates, same spot)

```mermaid
sequenceDiagram
    autonumber
    participant E1 as EntryGate 1
    participant E2 as EntryGate 2
    participant SR as SpotRepository
    participant S as Spot S1

    E1->>SR: tryClaim(S1, T1)
    E2->>SR: tryClaim(S1, T2)
    SR->>S: CAS null → T1
    S-->>SR: success (E1 wins)
    SR->>S: CAS null → T2
    S-->>SR: failure
    SR-->>E2: try next candidate
```

Atomic-claim guarantees only one gate gets each spot.

## 5. Reservation flow (V2)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant API as Reservation API
    participant DB as DB
    participant V as Vehicle (later)
    participant EG as EntryGate

    U->>API: POST /reservations (plate, start, end)
    API->>DB: try INSERT reservation HELD with spot from strategy
    Note over DB: overlap check on (held_spot, time-range)
    DB-->>API: HELD
    API-->>U: 201 { id, expires_at, amount_due }

    U->>API: POST /reservations/{id}/confirm (Idempotency-Key)
    API->>DB: status = CONFIRMED
    API-->>U: 200

    Note over V: arrives at start_time
    V->>EG: pull up (plate, type)
    EG->>API: lookup reservation by plate
    API-->>EG: CONFIRMED → spot ready
    EG->>EG: open barrier and convert reservation to ticket
```

## Output

```
Entry:    allocate via strategy → atomic CAS claim → ticket persisted → barrier opens
Exit:     quote → pay → close ticket → release spot → barrier opens
Concurrent: only one gate wins each spot via CAS
V2 reservation: HOLD then CONFIRM with overlap check
```
