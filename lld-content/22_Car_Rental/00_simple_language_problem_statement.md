# 00 · Car Rental — Simple Language Problem Statement

> Read this **before** `01_requirements.md`. The goal is to give you the gut-feel of what we're building, in plain language.

---

## The story

Aman wants to drive from Bangalore to Mysore for a long weekend — Friday 7 PM to Monday 9 AM, about 60 hours. He doesn't own a car. He opens the Zoomcar app, types his pickup location and time window, sees a list of available cars near him: a Hyundai Creta for ₹250/hr (₹15,000 for the trip), a Maruti Swift for ₹180/hr (₹10,800), a Toyota Innova for ₹350/hr (₹21,000).

He picks the Swift, taps **Book**. Pays a deposit. On Friday at 7 PM he walks to the parked car, opens the app, taps **Unlock** — the app verifies his GPS is within 50 m of the car, then the doors unlock. He drives to Mysore and back. On Monday morning at 8:45 AM he parks the car back in the original slot, taps **End Trip**, takes 4 photos of the car. The app computes the final fare:

- Base reservation fare ₹10,800
- Drove 720 km × ₹6/km = ₹4,320
- Returned 15 minutes early — no penalty
- Fuel: returned with 1/4 tank vs 1/2 at pickup → ₹600 fuel deduction
- No damages reported

**Total ₹15,720**, charged to his card. Receipt emailed.

Two days later, the cleaning crew finds a small dent on the bumper. A damage claim opens. They review the pre/post photos, decide it's the renter's fault, and charge an additional ₹3,500 to Aman's saved card — a week after the trip ended.

That whole flow looks simple. Underneath it has a dozen tricky problems:

- **Two users tap "Book"** on the same Swift for overlapping windows at the same millisecond. Only one wins.
- **Aman shows up 30 minutes late** — does his reservation expire? Do we still hold the car?
- **Aman returns 4 hours late** — late-fee policy, plus the next renter's reservation is now violated.
- **He returns to a different parking spot** than the original — geofence validation, possibly a relocation fee.
- **Damage claim weeks later** — must charge the original payment instrument idempotently, with fraud safeguards.

That's what we're designing.

---

## What is the user actually trying to do?

**Aman (the renter):**
- "Search cars near me available for these hours."
- "Show me prices, photos, fuel level, last inspection date."
- "Hold this car while I pay."
- "When I arrive, unlock it from the app."
- "Track me through the trip."
- "When I'm done, compute the final fare and charge me."
- "Let me cancel within X hours and get a refund per policy."

**Zoomcar Ops (the platform):**
- "Onboard new vehicles with photos, registration, insurance."
- "Set hourly rates per model, per city, per time of day."
- "Run pre/post-trip inspections."
- "Manage damage claims and disputes."
- "Schedule cleaning and servicing between trips."

**Cleaning crew / damage assessor:**
- "Show me cars that returned today."
- "Let me file a damage claim with photos."
- "Estimate the repair cost; submit for renter charge."

---

## Walk through one concrete example

Inventory:
- 1 Hyundai Creta (KA-01-AB-1234, current location Indiranagar, fuel 80%)
- 1 Maruti Swift (KA-01-CD-5678, current location HSR, fuel 50%)
- 1 Toyota Innova (KA-02-EF-9012, current location Whitefield, fuel 100%)

Each car has its own time-slot grid:
```
Swift KA-01-CD-5678
Fri 06:00-07:00 -> AVAILABLE
Fri 07:00-08:00 -> AVAILABLE
...
```

1. Aman searches "Bangalore, Fri 19:00 → Mon 09:00, 4-seater". App returns the Swift and the Creta within 5 km of his pin.
2. Aman taps Book on the Swift. System tries to atomically reserve all 60 one-hour slots from Fri 19:00 to Mon 09:00 → success. Reservation row created with status HELD, TTL 10 min, deposit ₹2,000.
3. Aman pays the deposit. Reservation transitions to CONFIRMED. The Swift is invisible to other searchers for that window.
4. Maya, 2 minutes later, searches the same window → sees only the Creta and Innova.
5. Friday 18:55 — push notification: *"Your Swift is ready for pickup at HSR."*
6. Aman walks to the car at 19:05. App detects GPS within 50 m of the car's last known location. He taps **Unlock** → server validates: reservation exists, time window includes now, GPS in fence → fires unlock command via car's IoT module → doors open → trip transitions PICKED_UP → IN_USE.
7. Aman drives. Trip records GPS breadcrumbs every 30 s for fare calc + safety.
8. Mon 08:45 — Aman parks the car back at HSR, taps **End Trip**, takes 4 photos.
9. Server computes fare: base ₹10,800 + km charge + fuel deduction. Pre-authorizes ₹15,720 against his saved card; trip transitions RETURNED.
10. Cleaning crew inspects Wed; finds a bumper dent. They open a DamageClaim. Assessor reviews → APPROVED at ₹3,500. Server charges Aman idempotently against the same card. Notification + invoice.

---

## What's tricky about this (and why we need an LLD at all)

1. **Time-slot atomic reservation.** A 60-hour rental spans 60 hourly slots. All 60 must be free **and** locked atomically — partial reservations are forbidden. One transaction with `INSERT ... ON CONFLICT DO NOTHING` across 60 rows; if any conflicts, abort and return all priors.
2. **The "two renters, same car, overlapping window" race.** Same as last-unit race in BookMyShow / E-Commerce, but smeared across a time grid. The unique-constraint guard is `(vehicle_id, hour_bucket)`.
3. **Pricing has two phases.** **Booking-time** locks the base reservation fare. **Return-time** computes per-km, fuel, late fees, etc. Both must be transparent to the user — show estimate up front, breakdown at return.
4. **Pickup geofence.** Server validates the rider's GPS before unlocking. Spoofing is a real attack — multi-source location (cell + GPS + Wi-Fi triangulation), velocity sanity check.
5. **Reservation drift.** User picked up 30 min late, returned 1 hr early. Final fare reconciles actual usage windows vs booked windows. Only **overage** is penalised; under-utilisation is the user's loss.
6. **Damage claims are async, money-bearing, and delayed.** Charging the user's card 5 days after the trip without fresh authorization is legally and technically tricky — pre-authorization at pickup with sufficient buffer, then capture only the actual amount; or use a saved-card "merchant-initiated transaction" with explicit consent at booking.
7. **Cancellation policy** is tiered. > 24 h to pickup = 100% refund. 6–24 h = 50%. < 2 h = 0%. Strategy pattern.
8. **No-show**. User doesn't pick up by start-time + 30 min grace → reservation auto-cancels, deposit forfeit per policy, slots returned to availability.
9. **Mid-trip extension.** User wants 2 more hours. Try to atomically reserve the 2 extra slots; if available, extend; if not, refuse.
10. **Vehicle-to-Model mapping.** Same as `Book vs Copy` in Library: a customer searches for "Hyundai Creta" (a Model); the system reserves a specific physical vehicle (with a VIN). When marketing said "Creta available", they meant the model class — but at booking we commit to one specific car.

These appear in `04_domain_model.md`, `05_database_design.md`, `08_sequence_diagrams.md`, and `11_concurrency_and_scaling.md`.

---

## Mental model in one line

> **A car rental system is hourly-slot inventory across many physical vehicles, with a reservation-to-trip lifecycle that locks slots atomically, geofences the unlock, computes fare in two phases (booking + return), and runs damage claims as a separate async workflow on the same payment instrument.**

---

## Where to go next

→ Open [`01_requirements.md`](./01_requirements.md). You'll see this story formalised into FRs / NFRs covering search, reservation, pickup, trip, return, pricing, cancellation, no-show, and damage claims.
