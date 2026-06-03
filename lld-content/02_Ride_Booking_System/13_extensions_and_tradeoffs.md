# 13 · Ride Booking — Extensions & Tradeoffs

## Tradeoffs (and why we chose what we did)

### 1. Authorization at request, capture at end

**Alternative**: charge upfront the estimated max.

**Chosen**: hold/authorize the upper bound; capture actual at trip end.

**Why**: refunding takes 3-5 business days; users hate it. Auth-then-capture is the industry standard.

### 2. Optimistic locking everywhere

Conflict rate is < 1% on rides; pessimistic locking would block parallel transitions and risk deadlocks.

### 3. Async match (vs sync match in the request)

- Sync: rider waits up to 15 s with no response. Bad UX.
- Async: API returns immediately ("looking for driver"). Match runs from `RideRequested` event.

### 4. Redis Geo for live driver location

PostGIS works at ~10-30 ms; Redis Geo at ~1 ms. Match latency budget forces Redis.

### 5. Single payment authorization

We don't pre-authorize per-km. Authorization for `estimate.max` covers all normal cases. If actual exceeds (rare), we re-auth or split-bill.

### 6. State pattern for ride (vs enum + map)

Cancellation fee depends on state. State pattern keeps the logic colocated with each state. Worth the extra classes.

---

## V2 extensions

### A. Pool / shared rides

Multiple riders share a vehicle in overlapping routes.

**Design changes:**
- `Ride.shareGroupId` — links riders sharing.
- `MatchingService` upgrades to **multi-rider matching**: greedy or constraint-based.
- `Ride` aggregate has multiple `pickup`/`drop` events per rider.
- Surge differs (pool gets discount).
- Driver compensation algorithm changes.

This is a **significant** redesign of matching but the rest of the system (payment, tracking) reuses largely unchanged.

### B. Scheduled rides

A rider books a ride for tomorrow 7 AM.

**Design changes:**
- `Ride.scheduledFor` timestamp + `SCHEDULED` status.
- A scheduler service triggers the match ~30 min before scheduled time.
- Estimate is given upfront with disclaimer ("subject to changes").
- Driver pool may be different (drivers opt into scheduled rides).
- No-show + driver no-show penalties bigger.

### C. Multi-stop trips

Rider goes A → B → C → D.

**Design changes:**
- `Ride.stops: List<Location>`.
- Pricing: per-segment computation.
- Tracking: progress per stop.
- Cancellation policy: more complex (partial completion).

### D. Corporate / B2B

Companies pay for employee rides.

**Design changes:**
- `CorporateAccount` aggregate.
- Cost center tracking per ride.
- Monthly invoicing instead of per-ride payment.
- RBAC for admins.

### E. Surge cap per region (regulatory)

Some jurisdictions cap surge (e.g., max 1.5× during emergencies). A `SurgeCapPolicy` strategy intercepts the SurgeAlgorithm output:

```java
factor = capPolicy.cap(rawFactor, region, time);
```

OCP win again.

### F. Heatmap for drivers

Tells drivers where demand is high.

**Design**: a streaming aggregator consumes `RideRequested` events, produces per-zone demand heatmaps to a Redis hash. Driver app polls per minute.

### G. Subscription (Uber One)

Free cancellations, no platform fee, no surge cap.

**Design**: a `SubscriptionRule` is added at the front of the pricing pipeline. Strategy + Chain pattern means no edits to existing code.

---

## Multi-region considerations

When we go multi-country:

- Each region has its own driver pool (drivers can't cross-region).
- Each region has its own DB shard or full stack.
- Currency, tax, regulatory rules differ — Strategy makes this clean.
- Cross-region only for analytics rollups.
- User accounts can be global (federated identity).

---

## Operational concerns

### Observability metrics

| Metric | Why |
| --- | --- |
| match_p95_seconds | Critical UX |
| acceptance_rate by city | Driver pool health |
| no_driver_rate by city | Indicates undersupply |
| surge_factor distribution | Pricing health |
| cancellation_rate by reason | Find friction points |
| trip_duration_anomaly | Safety / fraud signal |
| sos_response_time | Safety SLA |
| payment_capture_failures | Money integrity |

### Feature flags

Flag every major change: dispatch algo, pricing rule, cancellation fee, ML model.

### A/B testing

Built into Strategy injection. Each user gets a hash → a strategy variant.

### Chaos testing

- Kill match engine — verify backlog cron processes stuck rides.
- Slow Maps API — verify haversine fallback kicks in.
- Lose Redis Geo — verify PostGIS fallback.

---

## What this design will NOT support without rework

- **Helicopter / boat rides** — vehicle types with totally different routing/pricing semantics.
- **Multi-modal trips** — combining car + train + bike.
- **Auctions** — drivers bid for rides (we currently push, drivers accept).

These would require rebuilding matching and pricing.

---

## Tradeoff summary table

| Choice | Picked | Alternative | Why |
| --- | --- | --- | --- |
| Storage | Postgres | Cassandra | ACID + joins |
| Geo index | Redis Geo | PostGIS | Latency |
| Surge update | Server periodic (60s) | Real-time | Cost vs marginal accuracy |
| Match driver | Push offer | Driver pulls | Latency + control |
| Auth + capture | Two-phase | Charge upfront | Refund avoidance |
| Locking | Optimistic | Pessimistic | Low conflict |
| State pattern | Yes for Ride | Enum + map | Behavior diverges |
| Tracking | WebSocket fan-out | Polling | Bandwidth + latency |
| Region | Single | Multi | V1 simplification |
| Pool | Out | In | Major complexity |
