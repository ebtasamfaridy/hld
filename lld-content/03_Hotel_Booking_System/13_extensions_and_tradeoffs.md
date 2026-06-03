# 13 · Hotel Booking — Extensions & Tradeoffs

## Tradeoffs we made

### 1. Per-night calendar inventory rows

**Alternative**: track total bookings, compute availability by overlap query.

**Chosen**: pre-allocated row per (hotel, room_type, date) with `available_rooms`.

**Why**: avoids overlap queries (O(active bookings)) and races. Atomic UPDATE on a single row is the gold standard.

**Cost**: ~3.6 B inventory rows for 500K hotels × 10 room types × 730 days. We partition by date and rotate.

### 2. Booking transaction in a single Postgres TX

**Alternative**: split into Inventory Service + Booking Service over network with saga.

**Chosen**: V1 keeps both in one DB, single TX.

**Why**: simplicity, correctness. A 5-night booking is one COMMIT.

**When we'd split**: when inventory write rate exceeds Postgres node limits and we shard inventory differently from bookings.

### 3. Optimistic locking on Booking aggregate

Conflict rate is low; pessimistic locks would block parallel admin queries.

### 4. Search via Elasticsearch (eventually consistent)

**Alternative**: search hits Postgres directly.

**Chosen**: ES with CDC from Postgres.

**Why**: 50K RPS on filter+sort+date-range search; Postgres can't do that.

**Tradeoff**: ~30 s lag on inventory. Booking always re-validates.

### 5. Cancellation policy as snapshot in booking

**Alternative**: re-read hotel's current policy at cancel time.

**Chosen**: snapshot at booking time.

**Why**: contract integrity. If hotel changes policy tomorrow, your old booking still gets the original terms.

### 6. State pattern for Booking

Cancellation behavior diverges per state. State pattern keeps it cohesive.

### 7. Pricing token signed by HMAC

Stops tampering and stale prices. Required for any monetary system.

---

## V2 extensions

### A. Multi-room-type single hotel booking (group)

A guest books 2 deluxe + 1 suite at the same hotel.

**Design changes:**
- Introduce `BookingGroup` (root) → many `Booking` rows.
- Inventory decrements happen per room type.
- Single transaction across all room types.
- Customer-visible cancellation cancels the whole group.

**Why deferred**: doubles state machine complexity. Most bookings are single room type.

### B. Group bookings with workflow

10+ rooms. Often need approval, custom pricing.

**Design changes:**
- `GroupBookingRequest` aggregate with workflow (PENDING_QUOTE → QUOTED → CONFIRMED).
- Special pricing rules.
- Manual approval UI.

### C. Multi-currency

Hotel quotes in local currency; guest pays in their currency.

**Design changes:**
- `Money` already carries currency.
- Booking persists `(amount_local, fx_rate, amount_paid_currency)`.
- Daily FX rate snapshot.

### D. Loyalty programs

Points earned, points redeemed.

**Design changes:**
- `LoyaltyAccount` aggregate.
- `EarnRule` and `RedeemRule` strategies.
- Loyalty rule = a `PricingRule`.

### E. Dynamic / ML pricing

The base price changes hourly based on demand signals.

**Design changes:**
- Replace `BasePriceRule` with `MLPricingRule` that calls a pricing model.
- Cache predictions per (hotel, room, date) for 1 hour.
- Hotel admins can still set floors / ceilings.

The interface stays the same; the implementation differs.

### F. Channel manager (distribution)

Hotel listed on multiple platforms (Booking.com, Agoda, our platform). Inventory must stay consistent.

**Design changes:**
- A `ChannelManager` service syncs inventory two-ways.
- We become a node in a distributed inventory network.
- Conflicts resolved by timestamp + last-writer-wins, with reconciliation.

### G. Reviews and ratings

Full review system with moderation, helpful votes, response from owner.

**Design**: a `Review` aggregate; aggregations to hotel rating; moderation workflow.

### H. Wishlists, saved searches

User-side personalization.

---

## Operational considerations

### Observability

- **Booking funnel** — search, view, book, complete.
- **Conversion rate** by city, hotel, search query.
- **Inventory churn** — bookings vs cancellations per day.
- **Search → ES sync lag**.
- **Stuck bookings** > 5 min in PENDING.
- **Blocked dates with active bookings** — SRE alert.

### Feature flags

- Enable promo codes per region.
- Roll out new policies progressively.
- Test new search ranking algorithms.

### Reconciliation jobs

| Job | Frequency | What |
| --- | --- | --- |
| Inventory drift | hourly | sum of confirmed bookings × roomCount vs available_rooms |
| Stuck PENDING | every 5 min | cancel stale PENDING > 10 min |
| ES drift | daily | full-index check |
| Settlement | nightly | sum captured payments vs hotels' expected payouts |

### Backups

- Postgres PITR, daily full + WAL.
- Cassandra (if used) snapshot weekly.
- Object storage versioned (images, hotel docs).

---

## What this design will NOT support without rework

- **Resort packages** (room + meals + activities) — needs a higher-level "Package" aggregate.
- **Live chat with hotel** — separate messaging service.
- **Vacation rentals (Airbnb model)** — inventory is per-listing per-night, no room types. Adapt the calendar model but need owner workflows, photos at scale, reviews on hosts.

---

## Tradeoff summary table

| Choice | Picked | Alternative | Why |
| --- | --- | --- | --- |
| Inventory model | Per-night row | overlap query | Atomic CAS, no races |
| Locking | Atomic UPDATE WHERE | SELECT FOR UPDATE | Throughput |
| Policy | Snapshot in booking | live read | Contract integrity |
| State | State pattern | enum + map | Cancel logic varies a lot |
| Search | ES | Postgres | RPS + filters |
| Pricing token | HMAC | Re-quote at book | Trust + UX |
| Storage | Postgres partitioned | Cassandra | ACID for booking TX |
| Booking + Inventory | Same DB | Microservices | V1 simplicity |
| Multi-room-type | Out | In | V1 scope |
