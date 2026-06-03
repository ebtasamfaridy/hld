# 09 · Car Rental — State Machines

## 1. Reservation

```mermaid
stateDiagram-v2
    [*] --> HELD: place reserve, slots claimed, deposit auth pending
    HELD --> CONFIRMED: deposit auth succeeded
    HELD --> EXPIRED: TTL passed without confirm

    CONFIRMED --> ACTIVE: pickup completed (trip created)
    CONFIRMED --> CANCELLED: user cancels (refund per policy)
    CONFIRMED --> NO_SHOW: pickup window passed, no unlock

    ACTIVE --> COMPLETED: trip returned successfully

    EXPIRED --> [*]
    CANCELLED --> [*]
    NO_SHOW --> [*]
    COMPLETED --> [*]
```

Reservation is **immutable in window/vehicle/fare** once CONFIRMED. The status can advance, but the booked window doesn't change. Mid-trip extension creates a *new* small reservation appended to the same vehicle, not an edit of this one.

---

## 2. Trip

```mermaid
stateDiagram-v2
    [*] --> PICKED_UP: unlock command succeeded
    PICKED_UP --> IN_USE: first GPS ping or first km accumulated
    IN_USE --> RETURNED: end-trip succeeded
    PICKED_UP --> RETURNED: ended without ever moving (rare)

    IN_USE --> DISPUTED: forced end by ops, user disputed final fare
    RETURNED --> DISPUTED: damage claim filed, awaiting review
    DISPUTED --> RETURNED: dispute resolved in renter's favor or settled

    RETURNED --> [*]
    DISPUTED --> [*]
```

A Trip has a 1:1 mapping with a Reservation that reached ACTIVE.

---

## 3. Payment (per reservation)

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> AUTHORIZED: gateway auth ok (deposit)
    CREATED --> FAILED: gateway declined
    AUTHORIZED --> CAPTURED: at return, captured up to deposit
    AUTHORIZED --> VOIDED: cancellation or NO_SHOW with no forfeit
    AUTHORIZED --> PARTIALLY_VOIDED: capture < auth, remainder voided

    CAPTURED --> PARTIALLY_REFUNDED: late refund (e.g. damage rejected)
    CAPTURED --> REFUNDED: full refund
    PARTIALLY_REFUNDED --> REFUNDED: cumulative refund equals captured

    FAILED --> [*]
    VOIDED --> [*]
    REFUNDED --> [*]
    PARTIALLY_VOIDED --> [*]
```

Damage charges are **separate Payment-like rows** (see `damage_charges` table) — not a transition on the original Payment, because they happen days after `RETURNED`.

---

## 4. Vehicle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: onboarded
    ACTIVE --> MAINTENANCE: scheduled service or low fuel
    ACTIVE --> OUT_OF_SERVICE: damage report, accident, lost keys
    MAINTENANCE --> ACTIVE: service complete
    OUT_OF_SERVICE --> ACTIVE: returned to fleet
    OUT_OF_SERVICE --> RETIRED: end of life
    ACTIVE --> RETIRED: sold off
    RETIRED --> [*]

    note right of MAINTENANCE
      MAINTENANCE blocks new
      reservations but does not
      affect in-flight trips
    end note
```

Switching to MAINTENANCE is **prospective only** — current trip continues, but no future slots can be booked until ACTIVE again.

---

## 5. Damage Claim

```mermaid
stateDiagram-v2
    [*] --> REPORTED: ops opens claim with photos + estimate
    REPORTED --> UNDER_REVIEW: reviewer picked it up
    UNDER_REVIEW --> APPROVED: reviewer accepts
    UNDER_REVIEW --> REJECTED: pre-existing damage or insufficient evidence
    APPROVED --> DISPUTED: renter disputes within 7 days
    APPROVED --> CHARGED: MIT charge succeeded
    APPROVED --> DUNNING: MIT charge declined
    DUNNING --> CHARGED: retry succeeded
    DUNNING --> WRITE_OFF: ops gives up
    DISPUTED --> APPROVED: senior reviewer upholds
    DISPUTED --> REJECTED: senior reviewer overturns
    CHARGED --> [*]
    REJECTED --> [*]
    WRITE_OFF --> [*]
```

DUNNING also triggers a **user-level flag** that blocks new reservations until cleared.

---

## 6. TimeSlot

A timeslot has only two effective states (presence in DB):

```mermaid
stateDiagram-v2
    [*] --> ABSENT: free slot (no row)
    ABSENT --> RESERVED: INSERT timeslots row keyed by (vehicle_id, hour_bucket)
    RESERVED --> ABSENT: DELETE on cancel / no-show / completion / expiry
```

The slot doesn't carry status of its own — its presence/absence is the state. This is the simplest model that satisfies both correctness (PK enforces atomicity) and storage efficiency (only reserved slots stored).

---

## 7. User dunning flag

```mermaid
stateDiagram-v2
    [*] --> CLEAN
    CLEAN --> DUNNING: damage charge declined
    DUNNING --> CLEAN: payment received via support
    DUNNING --> WRITE_OFF: irrecoverable
    WRITE_OFF --> [*]
```

While in DUNNING, the user is blocked from booking new reservations. The status is checked at the front of `placeReservation`.

---

## Allowed actions per state (reservation)

| State | User can | Ops can |
| --- | --- | --- |
| HELD | Pay (→CONFIRMED), abandon (→EXPIRED) | Force-cancel |
| CONFIRMED | Cancel (→CANCELLED), pickup (→ACTIVE) | Reassign vehicle, force-cancel |
| ACTIVE | End trip (→COMPLETED) | Force-end, recover vehicle |
| COMPLETED | View receipt, dispute | Open damage claim, refund |
| EXPIRED, CANCELLED, NO_SHOW, COMPLETED | View only | View only |

---

## Output

```
Reservation:    HELD → CONFIRMED → ACTIVE → COMPLETED (+EXPIRED, CANCELLED, NO_SHOW)
Trip:           PICKED_UP → IN_USE → RETURNED (+DISPUTED arc)
Payment:        2-phase (AUTH → CAPTURE), partial refunds supported
Vehicle:        ACTIVE ↔ MAINTENANCE / OUT_OF_SERVICE → RETIRED
Damage Claim:   REPORTED → UNDER_REVIEW → APPROVED|REJECTED → CHARGED|DUNNING|DISPUTED
Timeslot:       presence/absence (no explicit status field)
User dunning:   CLEAN ↔ DUNNING → WRITE_OFF
```
