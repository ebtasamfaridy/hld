# 11 · Parking Lot — Concurrency & Scaling

## Concurrency hotspots

### 1. Spot allocation race
N gates × M cars in lane → simultaneous `requestEntry`. Without atomicity, two cars get the same spot.

**Mitigation**: `Spot.tryClaim` via CAS (`AtomicReference.compareAndSet` or `UPDATE … WHERE occupied=FALSE`). Strategy is responsible for retrying with the next candidate on lost CAS.

### 2. Ticket close vs operator override
Driver pays at exit gate; operator simultaneously hits "manual override" on the operator app. Both want to close the same ticket.

**Mitigation**: optimistic version on `tickets.version`; second writer gets CAS failure; UI displays "already closed."

### 3. Reservation overlap
Two users reserve the same spot for overlapping windows.

**Mitigation**: range-overlap query + insert in a single SQL transaction with `SELECT … FOR UPDATE` on the spot row, or `EXCLUDE USING gist` with `tstzrange` constraint.

### 4. Pricing change mid-park
Operator updates pricing while the ticket is ACTIVE. Which rate applies?

**Mitigation**: store `pricing_strategy_version` on the ticket at entry. The fee uses that version, not the current one.

### 5. Spot sensor desync
Sensor says spot is free but a vehicle is physically there.

**Mitigation**: prefer **booking state as truth** over sensor reads; sensors only inform alerts, not allocations. Periodic reconciliation reports.

## Threading

V1 in-process: requests are served by a thread pool. The atomic `Spot.tryClaim` is the only shared-state writer; everything else is per-request.

V2 server (HTTP):
- Each entry/exit gate is a **client**, not a thread.
- Backend sees concurrent HTTP requests routed by load balancer.
- DB does the atomic-claim work.

## Scaling — multi-lot operator (V2)

```
Lots:                  1 K
Total spots:           500 K
Backend RPS:           50–200
Storage:               2 GB/day audit
```

Single Postgres shard handles this easily. Sharding by `lot_id` becomes interesting beyond 10 K lots.

For dashboards, materialized views per lot per day refreshed nightly.

## Hot-path latency

| Op | Target | Notes |
| --- | --- | --- |
| `requestEntry` | < 50 ms | DB UPDATE + INSERT |
| `quote` | < 30 ms | Two SELECTs |
| `settle` | < 100 ms | Includes payment gateway |
| Dashboard read | < 200 ms | Cacheable |

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Gate offline | Queue requests locally; sync on reconnect |
| DB primary failover | Brief unavailability; gates display "service interrupted" |
| Payment gateway timeout | Retry; if still failing, allow operator manual override |
| Sensor stuck "occupied" | Allocation strategy excludes that spot; operator alert |
| LPR misread | Manual entry by attendant |

## Backpressure

Surge entry events (concert, mall sale):
- Strategy is O(spots) but with the partial index, free-spot lookup is O(log N).
- DB UPDATE is bounded by row-lock acquisition; under heavy contention, gates queue with a small wait — acceptable for the use case.

## Capacity numbers (single big lot, 5 K spots, 20 RPS peak)

```
DB connections:        2 per gate × 20 gates = 40
DB write/sec:          ~40 (entry+exit)
DB read/sec:           ~80 (quote + dashboard)
CPU/RAM:               minimal
```

This fits on a small Postgres instance.

## Output

```
Hotspots:    spot allocation CAS, ticket close vs override, reservation overlap,
             pricing-version-on-ticket, sensor desync
Threading:   stateless backend; DB is the synchronization point
Scale:       single Postgres handles 1 K lots / 500 K spots / 200 RPS
```
