# 02 · Vending Machine — Capacity Estimation

A single physical vending machine has trivial scale. Capacity matters when we operate a **fleet**.

## Single machine

```
Slots:          ~48
Products:       ~48 SKUs
Cash float:     ~₹5 K
Transactions:   ~100/day at peak
Audit events:   ~1 KB each → ~100 KB/day
RAM:            < 10 MB
CPU:            embedded ARM is enough
```

## Fleet (V2)

```
Machines:        50 K (across a city)
Daily txns/mc:   100
Total txns/day:  5 M
Backend RPS:     ~60 RPS sustained, ~500 RPS peak (lunch/evening rush)
Audit storage:   5 M × 1 KB × 365 = ~1.8 TB/yr
Heartbeats:      ~1/min/machine = 50 K × 60 = 3 K RPS
```

This is **read-light** for the central system; it's about ingesting heartbeats + audit, plus low-volume control plane.

## What forces the design

1. **Each machine is essentially independent.** No cross-machine atomicity needed for V1.
2. **Audit is durable, async** — write locally first, push to fleet backend later. Survives network outages.
3. **Heartbeats** are the only continuous traffic; bursty when refills happen (operator app pings).
4. **Fleet APIs** are mostly admin (operators); customer never talks to backend.

## Per-machine local state

```
Hot in RAM:      slot inventory, cash inventory, current state machine
Local SQLite:    audit log (for offline durability)
Sync to fleet:   audit log batched every 5 min when online
```

## Output

```
Single machine:  trivial; embedded compute
Fleet (V2):      50 K machines × 100 txn/day = 5 M txn/day
                 60 RPS sustained, 500 peak
                 1.8 TB audit/yr
                 Async sync via local SQLite + batch upload
```

The lesson: design **the machine** for correctness; design **the fleet** for low-throughput, eventually-consistent ingestion.
