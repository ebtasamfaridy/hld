# 14 · Car Rental — Interviewer Follow-ups

The questions a Staff+ interviewer is likely to ask. Each comes with a one-paragraph answer outline.

---

### 1. "Walk me through what happens, end-to-end, when a user clicks Book."

Idempotency lookup → return cached if duplicate. Validate user (KYC) and vehicle (active). Compute base fare from `hourlyRate × hours`. Atomically reserve all hourly slots in the window via `INSERT ... ON CONFLICT DO NOTHING` on `(vehicle_id, hour_bucket)`. If any conflict, rollback all priors and return 409 OUT_OF_STOCK. Authorize the deposit on the gateway with a deterministic idempotency key. Persist the Reservation row + Payment row + outbox event in one DB TXN. Return 201 to the user. Outbox publisher drains to Kafka — notification, analytics consume async. Total p99 budget ~600 ms; the gateway authorize is the dominant cost.

### 2. "Two users tap Book on the same Swift for overlapping windows in the same millisecond. How do you guarantee no double-booking?"

The PK `(vehicle_id, hour_bucket)` is the natural mutex. Both transactions try to INSERT the conflicting hour rows; Postgres serialises on the row-level lock; one TXN wins, the other gets `ON CONFLICT DO NOTHING` (zero rows inserted for at least one row). The losing app verifies row count post-insert, sees fewer than expected, rolls back its slots, never authorizes payment, and returns 409 OUT_OF_STOCK with the list of blocked buckets. The system is correct independent of how many concurrent attempts hit the same vehicle.

### 3. "Why hourly slots instead of continuous-time ranges?"

Atomicity. With slots, the PK enforces mutual exclusion as a side effect of `INSERT`. With ranges, you'd need `EXCLUDE USING GIST` constraints (Postgres-specific) or app-level locks — both more complex and less portable. The cost is edge truncation: a 19:15–21:45 booking actually claims 19:00, 20:00, 21:00 — three full hours. Acceptable for a self-drive product (people round to the hour anyway). For minute-rental scooters we'd use a different model (just-in-time CAS on vehicle status, no advance booking).

### 4. "What if the hot vehicle gets thousands of book attempts per second?"

Single-row contention on the slot rows would bottleneck. Mitigations: (a) **sharded counters** — split a popular SKU into N "buckets" and hash the reservation into one; (b) **front-of-queue** in Redis: a reservation queue per vehicle, drained by a single worker; (c) at the rate-limit layer, reject excess attempts with a "try again" response. We start with naive Postgres, profile, then add sharding only on observed hot vehicles.

### 5. "User clicks Book, network drops, they retry. How do you not double-charge?"

Two layers of idempotency. (a) **Our API**: client supplies `Idempotency-Key`, stored as `UNIQUE (user_id, idempotency_key)` on reservations. The second insert fails; we look up the existing reservation by key and return it. (b) **Gateway**: we pass our internal id as the gateway's idempotency key; the gateway dedupes on its side too. Belt-and-suspenders: even if our DB row doesn't exist (we crashed mid-TXN), the gateway dedupe prevents double-auth.

### 6. "Pickup happens. Then payment-capture fails at the return step. What now?"

The Trip is durable before capture (we mark `RETURNED` and persist final-fare in one TXN). Capture failure leaves the payment in `CAPTURE_PENDING`. A reconciliation job runs every few minutes: retries `capture(authId)` with the same idempotency key. After N retries, falls back to `void(authId)` and either issues an MIT charge against the saved card, or moves the user to DUNNING. The user might see "payment processing" briefly. We never lose a paid trip and never double-capture.

### 7. "What stops someone from spoofing GPS to unlock a car they're not standing next to?"

Multi-source location at the unlock call: GPS + cell-tower + Wi-Fi SSIDs. The server runs sanity checks: previous known location of this user 10 min ago, velocity sanity check (you can't be in HSR if you were in Whitefield 2 min ago). Plus device attestation (Apple App Attest, Play Integrity). And the actual physical layer — most modern fleet vehicles only respond to keys with a Bluetooth proximity check, so the modem confirms the renter's phone is within 5 m before opening the doors. Defense in depth.

### 8. "How do you split data across services / shards?"

Vehicles by city_id (most queries are city-scoped). Slot inventory by vehicle_id (consistent hash; spreads write load). Reservations by user_id (so "my bookings" is a single shard read). Trips by user_id (co-located with reservation). GPS breadcrumbs by trip_id (sequential writes per trip, partition naturally by time). Damage claims by trip_id.

### 9. "What's your strategy for searching available cars at scale?"

Search is on Elasticsearch with a per-vehicle availability summary refreshed via CDC from the slot inventory (~30 s lag). Geo filter via Redis GEO sorted by distance from the user. The query against ES is `(city, fits_window) AND (vehicleClass)` — the index is shaped for this. We accept eventual consistency on search; place-reservation re-validates strictly. Cache miss falls back to Postgres count(*) which is slower but correct.

### 10. "Damage assessment can result in a charge weeks later. How is that legal/technical?"

Legal: at booking, we record explicit consent for "merchant-initiated transactions for damages and late returns up to ₹X". This is the standard pre-authorization that car-rental operators have used for decades. Technical: the gateway supports MIT charges against a saved tokenized payment method (created at booking with consent). The damage-charge flow uses `claim_id` as the idempotency key. If declined, the user moves to DUNNING and is blocked from new bookings; ops handles recovery.

### 11. "Renter shows up 30 min late but the next booking is in 1 hour. Do you allow pickup?"

Yes. Pickup window is `[startAt - 15 min, startAt + 30 min]`. After +30 min it auto-cancels (NO_SHOW). The fact that the next booking is in 1 hour doesn't affect the current renter — only their final fare (late return penalty). If the late return collides with the next booking, ops gets alerted; we may dispatch an alternative vehicle to the next renter, comp them, and bill the first renter for the inconvenience.

### 12. "User wants to extend the trip mid-ride. How do you handle?"

Treat extension as a *new* mini-reservation on the same vehicle for the additional hour-slots. Atomic INSERT — if conflicts (next booking exists), reject 409 SLOTS_UNAVAILABLE. The user must return on time. If accepted, the original reservation's end_at stays unchanged but a "second leg" reservation appends. Pricing computes the union at return.

### 13. "The IoT modem times out on unlock. What's the user's experience?"

App shows "Connecting to vehicle..." for 5 s, retries once silently, then displays "Couldn't reach the car. Try again or call support." The reservation stays CONFIRMED — no Trip is created. Ops gets an alert. Most causes are transient (cell coverage). For repeated failures, ops dispatches a manual unlock and force-starts the trip from the operator dashboard. Refund of 1 hr fee as goodwill is automatic if delay > 30 min.

### 14. "Reservation TTL expires while user is on the payment page. What happens?"

The TTL sweeper runs every 30 s and only expires `WHERE status = 'HELD' AND expires_at < now()`. Status-guarded, so it can't expire a CONFIRMED reservation. If the sweeper wins the race vs the user's confirm, the user's confirm sees `UPDATE WHERE status = 'HELD'` updates 0 rows — we void the just-authorized payment, return 410 RESERVATION_EXPIRED, and ask the user to retry. UX-wise: warn at minute 8, expire at minute 10.

### 15. "How do you handle vehicles going offline mid-trip?"

The trip stays ACTIVE — we don't try to abort midway. Modem outages happen (basement parking, tunnels). Renter can manually start/stop ignition with the physical key. On return, we use the renter-reported odo + photos as primary; modem telemetry as secondary. If odo/photos look suspicious, the trip moves to DISPUTED for ops review.

### 16. "Walk through how you'd add 'one-way rentals' (pickup at A, drop at B)."

Three changes. (a) Reservation gains `pickupZoneId` and `dropZoneId`. (b) Drop fence at the new zone instead of the original. (c) Pricing adds a `OneWayRepositioningComponent` — flat fee or per-km between zones based on ops policy. Plus an async ops job to reposition the vehicle if needed (or wait for a renter going the other way). Inventory model unchanged — the slot grid is per-vehicle, not per-location.

### 17. "What if the same renter has 5 active damage disputes from 5 different rentals?"

Each claim is independent. Dispute resolution is per-claim. The renter is in DUNNING if any **approved + uncharged** claim exists. New bookings are blocked while in DUNNING. Once disputes resolve (or charges succeed), DUNNING clears. Worth noting: if all 5 are disputed simultaneously, the platform's fraud team is automatically alerted — pattern of disputes is a fraud signal.

### 18. "How do you compute fare at scale? Cron, on-demand, where?"

On-demand at trip-end. The user is online, expects to see the breakdown, presses "End Trip", server computes synchronously using `CompositePricing.breakdown(reservation, trip)` — a few millis. The breakdown is shown immediately. We then async-capture payment (deposit) + MIT for any difference. Later cron jobs reconcile capture failures, drift cases, and damage claims.

### 19. "GPS ingest at 1K writes/sec sustained — where does that go?"

Client batches 5–10 pings before sending. Server `POST /trips/{id}/ping` accepts a list, validates trip is ACTIVE, writes to a per-trip Kafka topic partition keyed by trip_id (preserves order). A sink consumer writes to Postgres for hot reads (last 7 days) and to S3 Parquet for cold (older). Reads are rare — only at trip end (which uses pickup+return GPS, not the breadcrumbs) and SOS / safety queries.

### 20. "What's the trickiest bug you'd anticipate, and how do you debug it?"

**Reservation drift**: actual usage shorter or longer than booked, leading to mis-billed late fees. Hard to catch in real time because the late-fee math is correct given the timestamps, but the timestamps may themselves be wrong (clock skew between client and server, daylight saving). Debugging: aggressive logging of every timestamp transition (booked end, actual return, server time, client claimed time), nightly reconciliation that cross-checks `late_fee_charged` vs `actual_late_minutes`, and an ops dashboard showing reservations whose final fare deviated from estimate by > 20%. The fix is usually defensive: always use server time for fee computation, never client-claimed, and keep both for audit.

---

## Closing remarks

If you internalise five things from this system, you can answer 90% of the car-rental LLD interview:

1. **Time-slot atomic reservation** via PK `(vehicle_id, hour_bucket)` — the no-double-book rule.
2. **VehicleModel vs Vehicle** — same shape as Library's Book vs Copy.
3. **Two-phase pricing** — base locked at booking, components added at return.
4. **Saga + outbox + idempotency** — same correctness spine as e-commerce.
5. **MIT for delayed damage charges** — legally backed by booking-time consent, technically dedup'd by claim_id.

Everything else (geofence, GPS ingest, IoT, cancellation tiers) is pattern reuse from neighboring systems.
