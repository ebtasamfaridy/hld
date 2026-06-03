# 01 · Car Rental — Requirements

## Functional requirements

### Catalog & search

- **FR-1** — Renter searches available cars by `(pickupLocation, pickupAt, dropAt, vehicleClass?)`. Results filter by **availability across the requested time window** + **distance from pickup point**.
- **FR-2** — Each result shows model, photos, fuel level at last return, distance from renter, total estimated fare, hourly rate.
- **FR-3** — A `VehicleModel` is the catalog entry (Creta, Swift). A `Vehicle` is a specific physical unit with VIN, plate number, current location, current fuel level, last-inspection date.

### Reservation (booking)

- **FR-4** — Renter selects a Vehicle and confirms time window. System runs `placeReservation` saga: validate license + KYC, atomically reserve all hourly slots in the window, authorize deposit, create Reservation row.
- **FR-5** — Reservation has TTL (10 min HELD before payment) and an `Idempotency-Key`. Repeat = same reservation, no duplicate.
- **FR-6** — Reservation transitions: HELD → CONFIRMED → ACTIVE (at pickup) → COMPLETED (at return). Plus CANCELLED, NO_SHOW, EXPIRED.

### Pickup

- **FR-7** — At pickup, the app validates: renter GPS within 50 m of vehicle location, current time within (pickupAt − 15 min, pickupAt + 30 min grace), reservation status = CONFIRMED.
- **FR-8** — Pre-trip inspection: renter takes 4 photos of the car (front, back, both sides). Optional dashboard photo for fuel + odo.
- **FR-9** — Server fires the IoT unlock command. Trip created in PICKED_UP state. Reservation transitions to ACTIVE.
- **FR-10** — If renter doesn't show by `pickupAt + 30 min`, reservation transitions to NO_SHOW; deposit forfeit per policy.

### Trip

- **FR-11** — While IN_USE, app records GPS breadcrumbs every 30 seconds for safety + km calculation.
- **FR-12** — Renter can extend the trip mid-ride if subsequent slots are available. Extension is a new mini-reservation.
- **FR-13** — Renter can lock/unlock from app during stops (no state change; just IoT command).
- **FR-14** — SOS: tap → notify ops + share live location.

### Return

- **FR-15** — Renter taps End Trip, takes 4 post-trip photos. Server validates renter GPS within 50 m of an approved drop-off zone (typically the original pickup, or a designated zone for one-way rentals).
- **FR-16** — Trip transitions RETURNED. Final fare computed: base + per-km + per-min overage + fuel deduction + late penalty + cleaning fee.
- **FR-17** — Pre-authorized deposit captured up to final fare. If fare > deposit + buffer, charge saved card for the difference. If fare < deposit, release the rest.

### Pricing

- **FR-18** — Base reservation fare = `hourly_rate × hours_in_window`. Locked at booking time.
- **FR-19** — Per-km charge = `km_driven × per_km_rate`. Computed at return.
- **FR-20** — Late return: graduated fee — first 30 min free, then double-rate per hour started.
- **FR-21** — Fuel: charged at `(fuel_at_pickup − fuel_at_return) × per_litre_rate × tank_size`. Skip if return ≥ pickup level.
- **FR-22** — Cleaning fee added if return photos show interior soiling (manual review).
- **FR-23** — Pricing config is per-city, per-model, per-time-of-day. Loaded from a `PricingPolicy` strategy.

### Cancellation

- **FR-24** — Renter can cancel a CONFIRMED reservation up to pickupAt. Refund per `CancellationPolicy`:
  - More than 24 h to pickup → 100% refund.
  - 6–24 h → 50% refund.
  - 2–6 h → 25% refund.
  - Less than 2 h → 0% refund.
- **FR-25** — Cancellation releases all reserved hour-slots, making the vehicle available to other renters.

### Damage claims

- **FR-26** — Ops staff can open a DamageClaim against a completed Trip. Each claim references the trip's pre/post photos.
- **FR-27** — Claim states: REPORTED → UNDER_REVIEW → APPROVED | REJECTED | DISPUTED. Approved claims charge the renter idempotently.
- **FR-28** — Renter can dispute within 7 days. Disputed claims escalate to a senior reviewer.
- **FR-29** — Charge attempt uses a "merchant-initiated transaction" against the saved payment method, consented at booking.

### Operator / admin

- **FR-30** — Onboard new vehicle (VIN, plate, location, photos, registration, insurance).
- **FR-31** — Mark vehicle in MAINTENANCE / OUT_OF_SERVICE → blocks new reservations on that vehicle.
- **FR-32** — Pricing config CRUD per city / model / time-of-day.
- **FR-33** — Damage claim workflow UI.

---

## Non-functional requirements

| ID | NFR | Target |
| --- | --- | --- |
| NFR-1 | Search p99 latency | < 250 ms (geo + time-window filter) |
| NFR-2 | Reservation place p99 | < 600 ms (incl. KYC check + slot reservation + payment auth) |
| NFR-3 | Unlock command p99 | < 2 s (IoT roundtrip) |
| NFR-4 | Inventory consistency | strong (no double booking, ever) |
| NFR-5 | Payment idempotency | 100% — never double-charge, including delayed damage charges |
| NFR-6 | Geofence false-reject rate | < 0.5% (poor GPS shouldn't block legitimate pickups) |
| NFR-7 | Reservation availability | 99.95% |
| NFR-8 | Fleet scale | 50K vehicles, 30 cities, 1M users |
| NFR-9 | Peak QPS | 5K search, 200 reservations, 1K trip status updates |

---

## Out of scope (V1)

- Insurance tier selection (default insurance only).
- One-way rentals (pickup ≠ drop). V1 is round-trip.
- Multi-driver per reservation.
- Long-term lease / subscription (> 1 month).
- B2B / corporate fleet rentals.
- Cross-city rentals.
- Loyalty / referral program.

---

## Edge cases the requirements MUST cover

- Two renters, same vehicle, overlapping windows → exactly one wins.
- User clicks Book twice → exactly one reservation.
- No-show: pickup window passes without unlock → auto-cancel + slot release.
- Late pickup within grace: allowed; extends nothing — drop time stays.
- Late return: allowed up to 4 hr with penalty; beyond that, IT calls + manual escalation.
- Reservation drift: actual usage shorter than booked → no refund (user's loss); longer → late penalty.
- Mid-trip extension when next slot is reserved by another user → refuse; user must return on time.
- Damage claim on a fully-refunded cancelled trip → impossible (trip never started).
- IoT unlock command times out → retry with idempotency token; fall back to ops support.
- Renter's saved card declined for damage charge → claim moves to DUNNING; renter blocked from new bookings until resolved.

---

## Output

```
Catalog:        VehicleModel → Vehicle (physical units with VIN)
Inventory:      time-slot grid (hour bucket) per vehicle
Reservation:    HELD → CONFIRMED → ACTIVE → COMPLETED (+ NO_SHOW, EXPIRED, CANCELLED)
Trip:           PICKED_UP → IN_USE → RETURNED (created at unlock)
Pricing:        2-phase (locked base at booking + variable at return)
Damage:         async claim workflow with merchant-initiated charge
Hard rules:     never double-book, never double-charge, never unlock outside fence
```
