# 01 · Vending Machine — Requirements

## Functional requirements

### Core
- Customer browses products, selects one (by slot code, e.g., `A1`).
- Customer inserts payment until ≥ price; machine accepts coins/notes (and optionally card).
- Machine dispenses the selected product and returns change.
- Customer can **cancel** mid-flow → machine refunds inserted money.
- Machine refuses if:
  - product out of stock,
  - cannot make exact change with current cash inventory.
- Service technician can **refill** products and cash, **collect** revenue, set prices, take machine offline.

### Slots and products
- Fixed slot grid (e.g., 8×6 = 48 slots).
- Each slot holds one product type with a stack (e.g., 10 cans).
- Products have ID, name, price (in minor units, e.g., cents/paise).

### Payment
- Coins: configurable denominations (₹1, ₹2, ₹5, ₹10).
- Notes: configurable (₹10, ₹20, ₹50, ₹100, ₹500).
- Optional card / UPI: external service returns success/failure.

### Out of scope
- Inventory analytics dashboards.
- Networked machine fleet management.
- Refrigeration / hardware control beyond an abstract dispenser.

### Extensions
- Loyalty / discount codes.
- Subscription cards (pre-paid balance).
- Remote refill notification (IoT integration).
- Multi-product "combo" purchases.
- Fraud detection (stuck-coin abuse).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Dispense latency | < 200 ms (after pay) | UX |
| Reliability | mid-dispense failure must not double-charge | financial integrity |
| Concurrent users | 1 (per machine) | physical hardware |
| Audit | every payment + dispense logged | reconciliation |
| Power loss | resume safely; refund pending payment | safety |

## Actors

```
Customer       — selects, pays, receives
Operator       — refills, collects, prices
Hardware       — dispenser motor, coin acceptor, note acceptor
PaymentGateway — for card/UPI (external)
Logger         — every event, durable
```

## Edge cases

| Case | Handling |
| --- | --- |
| Insert ₹500, item costs ₹35, but no change available | Reject the entire transaction up front (when product selected); show "exact change only" |
| Customer presses CANCEL after inserting ₹100 | Return ₹100 in coins/notes |
| Power loss during dispense | On reboot: detect "in-progress dispense"; alert operator; do not double-charge |
| Two customers tap simultaneously (button rebound) | Idempotency on (machineId, txnId) |
| Coin acceptor stuck in inserting state | Timeout 60 s; refund inserted coins; reset state |
| Operator refills product but with wrong price | Operator commits change with confirmation |
| Card payment timeout | Machine treats as failed; do not dispense |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Coin / note payment | ✓ | |
| Slot inventory + change-making | ✓ | |
| Operator mode | ✓ | |
| Card / UPI | minimal | full |
| Audit log | ✓ | |
| Loyalty | | ✓ |
| Fleet IoT | | ✓ |
| Combo buys | | ✓ |

## Output

```
Actors:        Customer, Operator, Hardware, PaymentGateway, Logger
Core FR:       select → pay (until ≥ price) → dispense + change; cancel; refill
Edge cases:    no change → reject; mid-dispense fail → no double charge;
               cancel → refund; idempotent button taps
NFR:           sub-200ms dispense, durable audit, safe power-loss recovery
```
