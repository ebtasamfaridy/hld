# 01 · Parking Lot — Requirements

## Functional requirements

### Core
- A parking lot has 1+ floors, each with N spots.
- Spot types: `BIKE`, `COMPACT`, `LARGE`, `EV` (with charger), `HANDICAP`.
- Vehicle types: `BIKE`, `CAR`, `TRUCK`, `EV_CAR`.
- Compatibility:
  - BIKE → BIKE | COMPACT | LARGE | EV | HANDICAP (with permit)
  - CAR → COMPACT | LARGE | EV (no charging) | HANDICAP (with permit)
  - EV_CAR → COMPACT | LARGE | **EV** (preferred for charging)
  - TRUCK → LARGE only
- Entry flow:
  - Vehicle arrives at gate → ticket issued → spot allocated (immediately).
  - Drive to spot → park.
- Exit flow:
  - Vehicle scans ticket → fee computed → payment → barrier opens.
- Multiple **entry/exit gates**.
- **Reservation** (V2): pre-book a spot for a future window; pay on arrival.
- **Pricing**:
  - Per-hour or per-15-minute slab.
  - Different rates per spot type.
  - Free first 15 minutes (configurable).
- Lot has a **capacity dashboard** (free spots per type per floor).
- Multiple **payment methods**: cash, card, UPI, wallet.

### Out of scope
- Lane-departure / parking-assist tech.
- Reservation no-show penalty fines (light extension).
- Multi-lot fleet management beyond a single lot.

### Extensions
- Reserved spots for monthly subscribers.
- Valet parking.
- Loyalty / corporate accounts.
- License-plate recognition (LPR) cameras for ticketless entry.
- Dynamic pricing (surge for events nearby).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Allocation latency | < 50 ms | Gate UX |
| Concurrent gates | 10–20 | Big lots have multiple lanes |
| Avg lot capacity | 500–5 000 | Small mall to airport |
| Audit | every entry, exit, payment | Reconciliation |
| Availability | 99.9 % | Closing the lot is bad UX |

## Actors

```
Driver        — arrives at gate, parks, leaves
Gate          — issues ticket / collects payment
EntryGate     — entry-only
ExitGate      — exit-only
Operator      — refunds, reset, pricing changes
PaymentGateway— external (cards / UPI)
LPR Camera    — optional, V2
```

## Edge cases

| Case | Handling |
| --- | --- |
| No spot available for vehicle type | Refuse entry; show "Lot full for this type" |
| Two cars at two gates request a spot at the exact same time | Atomic claim — only one wins |
| Driver pays but barrier fails to open | Audit; manual override at exit gate |
| Driver loses ticket | Charge max-day flat fee; verify license-plate |
| Driver overstays reservation | Charge up to overstay rate; release reservation |
| Power loss | Tickets persist (DB); on reboot, gates resume |
| Vehicle parked in wrong spot type | Detected only by sensor / LPR; charge the higher rate |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Multi-floor, multi-type spots | ✓ | |
| Strategy: allocation + pricing | ✓ | |
| Multi-gate entry / exit | ✓ | |
| Audit | ✓ | |
| Reservations | minimal | full |
| LPR / ticketless | | ✓ |
| Subscriptions / corporate | | ✓ |
| Dynamic pricing | | ✓ |

## Output

```
Actors:    Driver, Gates, Operator, PaymentGateway, LPR (V2)
Core FR:   spot allocation by type, pricing strategy, multi-gate, audit
NFR:       <50 ms allocation, 10–20 concurrent gates, 5 000 spot lots
Edge:      atomic claim, lost ticket, overstay, power loss
```
