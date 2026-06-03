# 13 · Car Rental — Extensions and Tradeoffs

## V2 features

| Feature | What it adds | Cost / risk |
| --- | --- | --- |
| **One-way rentals** | Pickup at A, drop at B | Routing: where can Vehicle be re-positioned? Pricing surcharge for ops |
| **Insurance tiers** | Basic / Plus / Zero-damage at booking | Damage-claim flow varies per tier; renter consent stored |
| **Multi-driver** | Friend can also drive on the same booking | Additional KYC + license; permission rules at unlock time |
| **Long-term subscription** | Per-month rental | Different inventory model (no slot grid; full month block) |
| **Self-pickup at airport** | Different geofence (the airport parking) | Geofence per pickup zone |
| **Loyalty / rewards** | Points earn + spend at checkout | Ledger system + integration as discount component |
| **Promo / coupon engine** | Code-based + auto-applied discounts | Specification pattern; abuse detection |
| **Live chat / SOS** | In-trip safety, real-time support | Out-of-band channel; trip events feed it |
| **Recommendations** | "Cars like the one you just rented" | Real-time personalisation; offline ML pipeline |
| **Damage prediction (ML)** | Pre-classify post-trip photos | Reduces ops review backlog 70%; same Damage flow underneath |
| **Surge pricing** | Hourly rate flexes with demand | Pricing strategy that reads supply/demand telemetry |
| **EV charging integration** | "Battery at 30% — please charge before return" | New `Charging` component in pricing; map of charging stations |
| **Corporate / B2B fleets** | Org-level billing, monthly invoices | Multi-tenancy + invoicing pipeline |
| **Cross-city rentals** | Pickup Bangalore, drop Mumbai | Major: routing, ops repositioning, pricing |

---

## Architectural tradeoffs

### Hourly slots vs continuous time

**Hourly slots (chosen V1)**
- Pro: PK `(vehicle_id, hour_bucket)` is the natural mutex; reservations are atomic INSERT statements.
- Pro: Simple availability query — "is bucket free?" is a key lookup.
- Con: User who books 19:15 → 21:45 actually locks 19:00, 20:00, 21:00 — losing partial hours at the edges. Acceptable for a 30-day-rental product; less so for a per-minute scooter.

**Continuous time (range trees)**
- Pro: Exact-minute reservation; no edge truncation.
- Con: Range overlap detection is harder to make atomic; usually requires GIST indexes (Postgres `tsrange`), more complex query, slower.

**Hybrid**: hourly slot grid + an explicit "extension request" for partial hour, which costs the same as a full hour. We chose this; document it transparently to users.

### Reserve-then-pay vs Pay-then-reserve

**Reserve-then-pay (chosen)**
- Pro: User never gets "paid but no car".
- Con: Holds inventory during checkout; we use 10 min TTL.
- Con: Attacker can lock a popular car by starting many checkouts.

**Pay-first**
- Pro: No inventory hostage.
- Con: Synchronous gateway latency on the hot path; refund flow on rare conflicts.
- Con: Bad UX — "Sorry, slot was just taken; we're refunding you" hits trust.

We chose reserve-first. Mitigations: short TTL, per-user reservation cap, captcha on suspicious bursts.

### Pre-authorize buffer vs flat deposit

**Pre-authorize a buffer** (chosen — 1× base fare)
- Pro: Captures any reasonable final fare without re-asking the user.
- Con: User sees a hold on their card that can confuse them.

**Flat deposit + delayed charge**
- Pro: Lower visible hold.
- Con: More frequent MIT charges → higher decline risk.

Tradeoff: at booking we authorize `deposit ≈ baseFare`, capture exactly the final total at return, MIT for any difference.

### Damage claim async vs synchronous

**Async (chosen)**
- Pro: Trip-end is fast (close in 2 s).
- Con: Charge happens days later, requires legal consent at booking + MIT capability.

**Synchronous on return**
- Pro: User present, accepts/disputes immediately.
- Con: Blocks return until ops can review; hostile to UX in night-time / unstaffed lots.

We chose async, and mitigate the legal complexity with explicit MIT consent at booking and a 7-day dispute window.

### Hourly slot rows vs single "blocked range" row

**One row per slot (chosen)**
- Pro: PK gives O(1) atomic mutex via `INSERT ON CONFLICT DO NOTHING`.
- Con: 60 rows per long rental → more storage and inserts.

**Single range row**
- Pro: 1 row per reservation.
- Con: Atomic "is range free" requires `EXCLUDE USING GIST` constraints (Postgres-specific) or app-level locking. Less portable.

We picked the row-per-slot model for simplicity and cross-engine compatibility.

### IoT directly from app vs through backend

**Through backend (chosen)**
- Pro: Server-side validation of reservation + GPS + idempotency; audit trail; rate-limit.
- Con: 2-hop latency vs direct app → modem.

**Direct from app**
- Pro: Faster unlock (~1 s instead of 2 s).
- Con: App can be reverse-engineered; trusting the client to "say I'm at the car" is a security regression.

Backend-mediated unlock is the security-correct choice. Latency overhead is acceptable.

### Vehicle-Model + dynamic allocator vs vehicle-direct booking

**Allocator picks the unit** (chosen)
- Pro: Renter searches for "Creta" — system picks the best Creta; ops can move the assignment if a vehicle becomes unavailable.
- Con: Renter can't pick their favorite specific car (some users care).

**Vehicle-direct**
- Pro: User chooses exactly KA-01-CD-1234.
- Con: One sick vehicle → reservation invalid, manual ops intervention to swap.

We do allocator-picks for V1. V2 can add "favorite vehicle" to power users while still falling back gracefully when their fave is unavailable.

---

## Tradeoffs in plane separation

The browse plane is intentionally **eventually consistent** with the inventory truth. ES is updated via CDC, lag ≤ 60 s.

| Aspect | Browse plane | Buy plane |
| --- | --- | --- |
| Consistency | Eventual (≤ 60 s) | Strong |
| Latency | < 250 ms | < 600 ms |
| QPS | 5K peak | 200 peak |
| Data store | ES + Redis | Postgres |
| Failure impact | Search degrades | Cannot reserve |

If the user sees "available" in search but place-reservation fails with OUT_OF_STOCK, they retry. We surface alternative vehicles automatically. This is the right tradeoff — making search strongly consistent would kill latency without a UX gain.

---

## What we'd do differently for "minute-rental" (Bird, Lime, scooter sharing)

Minute-rental changes the constraints:
- Inventory is per-second, not per-hour. Slot grid would need 60× more rows or a different model entirely.
- Every "park anywhere" pickup means dynamic geofences, not fixed parking spots.
- Pricing is per-minute, not per-hour. Per-km is the dominant component, not base.
- Trips are < 30 min typically. The whole reservation+trip model collapses into a single "ride" with no advance booking.

For minute-rental we'd drop the slot-reservation model entirely and use **just-in-time grab**: user taps a vehicle on the map → atomic state transition `IDLE → IN_USE` with a CAS column. Same shape as Ride Booking dispatch.

---

## What we'd do differently for "Turo / P2P marketplace"

Turo adds a host (vehicle owner) as a third actor:
- Host onboards, sets prices, sets availability calendar.
- Renter books from a host; host approves or auto-approves.
- Money flow: renter pays platform → platform escrows → host gets paid post-trip.
- Insurance is platform-provided.

The reservation model stays similar. The big additions:
- Host approval workflow (synchronous or auto).
- Multi-seller payout ledger (same as e-commerce).
- Insurance escrow account.
- Owner / renter ratings on both sides.

Architecturally this is `21_ECommerce` patterns + this car rental's slot model.

---

## Output

```
V2:        one-way, insurance tiers, subscription, EV charging, surge, B2B
Tradeoffs: hourly slots vs continuous, reserve-first vs pay-first, async damage, IoT through-backend
Plane:     browse (eventual) vs buy (strong) — by design
Variants:  minute-rental drops slots entirely; Turo adds a host actor
Underneath: same atomic-mutex pattern as e-commerce inventory
```
