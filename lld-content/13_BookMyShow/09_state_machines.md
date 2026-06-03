# 09 · BookMyShow — State Machines

## Seat (per-show) state

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE
    AVAILABLE --> HELD : tryHold (Redis SETNX) succeeds
    HELD --> AVAILABLE : TTL expires OR explicit cancel OR conflict rollback
    HELD --> BOOKED   : confirm (Postgres TX)
    BOOKED --> CANCELLED : booking cancelled (refund flow)
    AVAILABLE --> BLOCKED : admin block (e.g., damaged seat)
    BLOCKED --> AVAILABLE : admin unblock
```

`HELD` is **transient** — Redis owns the truth via TTL.
`BOOKED` is **durable** — Postgres + PK constraint owns the truth.

## Hold lifecycle

```mermaid
stateDiagram-v2
    [*] --> HELD : create
    HELD --> CONFIRMED : confirm + payment success
    HELD --> EXPIRED   : TTL expiration (lazy update via DB job or on-read)
    HELD --> CANCELLED : user-initiated DELETE
    CONFIRMED --> [*]
    EXPIRED --> [*]
    CANCELLED --> [*]
```

The `HELD → EXPIRED` transition has a subtle aspect: the Redis key auto-expires, but the row in the `holds` table needs status updates for analytics. We update on-read (when someone tries to confirm an expired hold) or via a low-priority sweeper.

## Booking lifecycle

```mermaid
stateDiagram-v2
    [*] --> CONFIRMED : confirm succeeds
    CONFIRMED --> CANCELLED : user cancel within refund window
    CONFIRMED --> CANCELLED : show cancelled (admin)
    CANCELLED --> REFUNDED  : refund processed
    REFUNDED --> [*]
    CONFIRMED --> ATTENDED  : show happens (informational)
```

## Show lifecycle (admin)

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED
    SCHEDULED --> OPEN : booking opens (configurable lead time)
    OPEN --> RUNNING   : show starts
    RUNNING --> DONE   : show ends
    SCHEDULED --> CANCELLED
    OPEN --> CANCELLED
```

When a show transitions to `RUNNING`, no new bookings are accepted (server-side guard).

## Output

```
Seat:      AVAILABLE ↔ HELD → BOOKED (or back to AVAILABLE on TTL/cancel)
Hold:      HELD → CONFIRMED | EXPIRED | CANCELLED
Booking:   CONFIRMED → CANCELLED → REFUNDED
Show:      SCHEDULED → OPEN → RUNNING → DONE; CANCELLED at any pre-RUNNING state
```
