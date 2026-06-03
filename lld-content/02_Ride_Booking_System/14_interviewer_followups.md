# 14 · Ride Booking — Interviewer Follow-ups

> 90 seconds, out loud. Practice each.

---

## Q1. "Two riders request a ride at the exact same second; only one driver is available nearby. How do you ensure only one rider gets that driver?"

> Match engine selects candidates and then transitions the chosen driver from `IDLE → OFFER_PENDING` via a SQL CAS:
>
> ```sql
> UPDATE drivers SET status='OFFER_PENDING', version=version+1 WHERE id=? AND status='IDLE' AND version=?
> ```
>
> Only one update succeeds. The losing match retries with the next candidate or marks the ride `NO_DRIVERS_FOUND`. We also use `FOR UPDATE SKIP LOCKED` on the candidate selection itself so concurrent matchers don't even pick the same candidates.

---

## Q2. "How do you compute surge?"

> Per (zone × ride type), we recompute every 60 s. Inputs: idle drivers, pending requests, historical baseline. Output: factor in [1.0, 3.0]. Stored in Redis with TTL.
>
> Crucially, when the rider gets an estimate, we sign the surge factor in an HMAC token. That token is sent with the booking request; the server uses the locked factor for billing. This prevents both **unfair surges to the rider** (reading a fresh higher factor) and **rider abuse** (holding stale low factors).

---

## Q3. "What happens if the rider's app loses connectivity mid-ride?"

> The trip continues on the driver's side. Tracking (WebSocket) reconnects automatically. The trip end is driver-initiated; the rider doesn't need to be online.
>
> If the rider was paying via in-app, the post-trip flow runs against the stored payment method. If they need to dispute, support has the full ride event log.

---

## Q4. "How do you handle SOS?"

> SOS is its own service with the highest priority queue. The `POST /safety/sos` endpoint:
>
> 1. Writes an encrypted SosEvent (rider, driver, ride, location, timestamp).
> 2. Pages the safety on-call team with live tracking enabled.
> 3. In some regions, forwards to a partnered law-enforcement API.
>
> Audio/video recording (where legal) was triggered before. Notifications go to the rider's emergency contact list.
>
> This service is **fail-closed**: if a write fails, we retry with high priority and surface UI feedback. We never silently drop an SOS.

---

## Q5. "How do you scale the matching engine?"

> Several axes:
>
> 1. **Per-city sharding**: each city has its own match instance with its own Redis Geo set. Bangalore traffic doesn't touch Mumbai.
> 2. **Async event-driven**: ride requests go on Kafka, matchers consume per-city partition.
> 3. **Hot Redis Geo**: <2 ms `GEOSEARCH` keeps query latency tiny.
> 4. **Per-zone parallel**: within a city, match workers can process disjoint zones in parallel.
> 5. **Backpressure** if match queue grows: scale workers horizontally; alert if queue depth > threshold.

---

## Q6. "Walk me through what happens if the Maps API goes down."

> Two callers: PricingService (estimate distance/ETA) and MatchingService (route preview).
>
> Both wrap calls in a circuit breaker. Open-circuit fallback:
>
> - **Pricing** — uses haversine distance × 1.3 (typical road factor) and a city-average speed. Less accurate but service continues.
> - **Matching** — uses Redis Geo straight-line distance for ranking; same fallback already used for cold path.
>
> We also surface a soft banner in the rider app: "Estimates may vary slightly while we're updating routes." Honesty preserves trust.

---

## Q7. "How do you handle a malicious driver who keeps cancelling after accepting?"

> Multi-layer:
>
> 1. **Real-time penalty** — every cancel after accept reduces the driver's matching priority for the next 30 minutes.
> 2. **Compensation to rider** — small credit auto-applied.
> 3. **Pattern detection** — ML model flags drivers with >5% cancel-after-accept rate.
> 4. **Graduated bans** — warning → 24 h → 7 d → permanent.
>
> All actions logged for audit and appeals.

---

## Q8. "What's your idempotency strategy across the booking lifecycle?"

> Several keys, layered:
>
> - `Idempotency-Key` on `POST /rides` — stops duplicate bookings.
> - Payment authorize uses `ride_id + ":auth"` as the gateway idempotency key — stops double-auth on retry.
> - Payment capture uses `ride_id + ":capture"` — stops double-capture on retry.
> - State transitions are CAS — at-most-once per version increment.
> - Outbox events have `event_id`; consumers dedupe by it.

---

## Q9. "How do you ensure data consistency across the Ride aggregate and the Driver aggregate?"

> They are separate aggregates with separate transactions. We use **events** + **idempotent handlers** for cross-aggregate updates.
>
> Example: when ride completes (Ride aggregate transaction), we publish `RideCompleted`. A handler in the driver context consumes and transitions the driver IN_TRIP → IDLE.
>
> Failures: if the event isn't processed, a reconciliation cron detects "Driver in IN_TRIP for > 30 min with completed ride" and corrects.
>
> We trade strong cross-aggregate consistency for **availability** + **eventual correctness**, with reconciliation as the safety net.

---

## Q10. "What do you do if the same driver shows up in 2 cities (location anomaly)?"

> Anomaly detection on driver location stream:
>
> - If a driver "teleports" (Δdistance / Δtime > 200 km/h), we flag.
> - Flagged drivers are temporarily set OFFLINE and notified to verify location.
> - Repeat offenders go to fraud review.
>
> Real-world cause: GPS spoofing apps. We also run device-attestation (SafetyNet on Android, DeviceCheck on iOS).

---

## Q11. "Pool / shared rides — how would you extend this design?"

Refer to `13_extensions_and_tradeoffs.md`. Key changes:
- `Ride.shareGroupId`.
- Multi-rider matching strategy.
- Per-rider pickup/drop events.
- New pricing for pool.
- Driver compensation differs.

The strategy + state patterns we already use accommodate this; the matching algorithm is the hard part.

---

## Q12. "What if the rider enters wrong drop location and corrects mid-trip?"

> Two flows:
> - Pre-IN_TRIP: cancel and rebook (rare; usually driver helps).
> - In-trip: drop edit endpoint within X km/min of original; recomputes fare; driver is notified.
>
> Both involve a `DropEditEvent`. Pricing recomputes. The rider sees the updated estimate.
>
> We don't allow infinite mid-trip edits — abuse risk.

---

## Q13. "How do you measure whether the matching engine is healthy?"

> Four KPIs:
>
> - **p95 match latency** (request → assignment).
> - **No-driver rate** by city/zone — indicates undersupply.
> - **Driver acceptance rate** — too low → bad offers; too high → maybe over-rewarding.
> - **First-attempt success rate** — how often the top-scored driver accepts.
>
> Plus health checks on Redis Geo, DB, Kafka.

---

## Q14. "Designing for a small city vs a global service — what changes?"

> For a small city: monolith app + single Postgres + single Redis. ~5 K rides/day.
>
> Global service: per-region stacks, cross-region replication for users only, federated identity, multi-currency, regulatory variants. Strategy + per-region config + Outbox/Saga for cross-service consistency.
>
> The core domain (Ride, Driver, Match) is the same. The infra around it scales.

---

## Q15. "Surge made me angry as a rider once. How would you reduce that anger?"

> Three levers:
>
> 1. **Transparency** — show why surge is high (high demand area, time of day).
> 2. **Cap** — max factor (e.g., 1.5×) during emergencies, regulated by region.
> 3. **Alternatives** — recommend a slightly different pickup point or wait 5 minutes.
>
> Riders accept surge when they understand it. Surprise is the enemy.

---

Practice each. Aim for 90 seconds and a clean structure (claim, justification, alternative).
