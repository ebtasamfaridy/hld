# 09 · Parking Lot — State Machines

## Spot lifecycle

```mermaid
stateDiagram-v2
    [*] --> FREE
    FREE --> CLAIMED  : tryClaim(ticketId)
    CLAIMED --> FREE   : release(ticketId)  (via exit OR manual override)
    FREE --> RESERVED  : reservation HELD/CONFIRMED for window
    RESERVED --> CLAIMED : reservation activated on entry
    CLAIMED --> OUT_OF_SERVICE : sensor fault / damage
    FREE --> OUT_OF_SERVICE : maintenance
    OUT_OF_SERVICE --> FREE : restored
```

For V1 we only have FREE ↔ CLAIMED (+ OUT_OF_SERVICE). Reservations are V2.

## Ticket lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE     : ticket issued at entry
    ACTIVE --> PAID    : payment success
    PAID --> CLOSED    : barrier opens, spot released
    ACTIVE --> CLOSED  : manual override (no payment, e.g., damage / waive)
    ACTIVE --> ACTIVE  : tariff updated (re-quote on each scan)
```

`PAID → CLOSED` is the boundary where the barrier physically opens. Up to that point, the customer can refuse and walk away (rare); on `PAID` we hold the spot and refund only via operator.

## Reservation lifecycle (V2)

```mermaid
stateDiagram-v2
    [*] --> HELD       : POST /reservations
    HELD --> CONFIRMED : confirm (payment)
    HELD --> EXPIRED   : timeout 15 min
    CONFIRMED --> ACTIVATED : vehicle arrives in window
    CONFIRMED --> NO_SHOW   : window passes without arrival
    HELD --> CANCELLED : user cancels
    CONFIRMED --> CANCELLED : user cancels (refund policy applies)
    ACTIVATED --> CLOSED   : vehicle exits (becomes a regular ticket)
```

Note the parallel with hotel booking — same shape.

## Lot mode

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> CLOSED : operator closes (off-hours / capacity)
    CLOSED --> OPEN : operator opens
    OPEN --> EVAC : emergency
    EVAC --> OPEN : restored
```

In CLOSED / EVAC, gates refuse new entries; existing tickets can still exit.

## Output

```
Spot:         FREE → CLAIMED ↔ FREE; OUT_OF_SERVICE branch
Ticket:       ACTIVE → PAID → CLOSED (or ACTIVE → CLOSED via override)
Reservation:  HELD → CONFIRMED → ACTIVATED/NO_SHOW; (HELD → EXPIRED) is the auto-collapse
Lot:          OPEN ↔ CLOSED ↔ EVAC
```
