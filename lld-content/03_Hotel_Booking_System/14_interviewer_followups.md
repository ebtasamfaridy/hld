# 14 · Hotel Booking — Interviewer Follow-ups

> 90 seconds, out loud.

---

## Q1. "Two guests try to book the last room on the same date. What happens?"

> Inventory rows are pre-allocated per (hotel, room_type, date) with `available_rooms`. Booking decrement is a single SQL statement:
>
> ```sql
> UPDATE room_inventory SET available_rooms = available_rooms - 1
> WHERE hotel_id=? AND room_type_id=? AND date=?
>   AND blocked = FALSE AND available_rooms >= 1;
> ```
>
> Postgres runs both UPDATEs sequentially against the same row at the storage layer. One returns 1 affected row, the other 0. The losing booking's transaction throws and rolls back, releasing any nights it had reserved. The winning booking continues to payment auth and persistence.

---

## Q2. "How do you handle a 5-night booking where night 3 is unavailable?"

> The whole booking is one transaction. We loop dates and decrement; if any UPDATE returns 0 rows, we throw. The `@Transactional` ROLLBACK undoes nights 1 and 2 automatically.
>
> The user gets `409 INVENTORY_UNAVAILABLE` with the conflicting night listed. They can adjust dates and retry.
>
> No payment is captured, no booking row exists, no inventory is in a half-consumed state.

---

## Q3. "How do you compute price?"

> A pipeline of `PricingRule` strategies:
>
> 1. `BasePriceRule` — sum of nightly rates from inventory.
> 2. `SeasonalRule` — peak / off-peak markup.
> 3. `OccupancyRule` — markup as availability drops.
> 4. `LastMinuteRule` — discount for 24-hour bookings.
> 5. `LengthOfStayRule` — discount for 7+ nights.
> 6. `PromoCodeRule` — apply coupon.
> 7. `TaxRule` — region-specific.
>
> The result is a `PriceBreakdown` with per-night line items + totals. The server signs an HMAC token; the client passes it back at booking. This locks the price.

---

## Q4. "What's the cancellation policy lifecycle?"

> When a hotel onboards, they pick a policy (Flexible / Standard / Strict / NonRefundable). Each booking snapshots the policy at booking time.
>
> On cancel, we ask `policy.refundFor(booking, now)` and apply the refund. The hotel can change their default policy later, but already-confirmed bookings keep their original snapshot. This protects the contract.
>
> We also support per-room-type policies (a luxury suite may be stricter than a standard room).

---

## Q5. "How do you prevent overselling under high contention?"

> Three layers:
>
> 1. **Atomic SQL UPDATE** — the lowest level guarantees correctness.
> 2. **Read-modify-write avoided** — application never decides between read and write; it's all one statement.
> 3. **Reconciliation cron** — daily job sums confirmed bookings × roomCount per (hotel, room_type, date) and checks against `total_rooms - available_rooms`. Drift triggers an alert.
>
> The cron is a safety net; the SQL is the real defense.

---

## Q6. "How does search stay fast and consistent?"

> Search hits Elasticsearch. ES is updated via Debezium CDC from Postgres WAL — typically < 30 s lag.
>
> Search results are cached in Redis (top queries by (city, date_range) hashed). Cache TTL is 30 s.
>
> Booking flow always re-validates against Postgres atomically — search showing stale availability doesn't oversell.

---

## Q7. "Walk me through extending the system to support multi-room-type bookings."

> Add a `BookingGroup` aggregate. Each group has multiple `Booking` rows (one per room type). The group has total price; cancellations cancel the whole group; modifications can adjust per-row.
>
> Inventory: we still decrement per (room_type, date) atomically. The transaction now spans more rows.
>
> Pricing: `PricingService` computes per booking, then the group sums.
>
> State machine: each Booking has its own state (mostly synced); the Group also has a state.
>
> Most existing code (PricingService, InventoryService, payment) reused.

---

## Q8. "What if the hotel marks a date blocked while we have a booking?"

> Block-with-check: when admin POSTs `:block`, we first query active bookings (CONFIRMED, CHECKED_IN) overlapping the range. If any exist, we 409 with details.
>
> Admin can override with `force=true`. In that case, we cancel the affected bookings, refund per the original policy (with override notes), and notify the guests. We never silently invalidate.

---

## Q9. "How do you scale this 10×?"

> Path:
>
> 1. **Read replicas** for Postgres.
> 2. **Vertical scale** for primary.
> 3. **Partition** inventory by month (already done).
> 4. **Shard** by `hotel_id` when write rate exceeds 10K/sec.
> 5. **ES cluster** sized for search; CDN for static.
> 6. **Cache** popular hotel detail and availability snapshots.
> 7. **Per-region stacks** when multi-country.
>
> The booking service stays stateless; horizontal scale via K8s HPA.

---

## Q10. "What's the role of the price token?"

> A signed (HMAC) JWT-like blob containing the price snapshot the client saw, plus an `exp` (5 min). On `POST /bookings`, the server verifies:
>
> - HMAC matches (no tampering).
> - Not expired (price is fresh).
>
> If valid, the server uses the snapshot for billing. This prevents:
>
> - Guests booking at stale low prices.
> - Server changing price at the last instant.
> - Tampering ("I'm sending a fake low price").

---

## Q11. "What happens when payment is captured but DB write fails?"

> Three layers of defense:
>
> 1. **Order of operations** — we try the DB INSERT inside the same transaction. We auth payment first; if DB fails, the auth is voided.
> 2. **Outbox** — events are inside the same TX as the booking.
> 3. **Reconciliation** — if a payment exists without a booking, refund automatically.
>
> Auth-vs-capture separation also helps: auth holds money but doesn't move it. Capture happens after we know the booking is durable.

---

## Q12. "Tell me about the inventory pre-population strategy."

> Daily cron rolls forward inventory: every hotel × room_type gets a row 365 days out. Past rows older than 6 months get dropped (or archived).
>
> Why pre-populate? Because the atomic UPDATE assumes the row exists. INSERT-on-miss would be racy.
>
> Hotels can override the prefilled `total_rooms` and `base_price` per range via the bulk update API.

---

## Q13. "How would you support dynamic pricing (ML-driven)?"

> Replace the `BasePriceRule` with one that consults a prediction service:
>
> - Inputs: hotel, room_type, date, day of week, lead time, recent booking velocity, weather, events.
> - Output: nightly price.
>
> Cache predictions in Redis for 1 hour. Hotel can set floors/ceilings.
>
> The interface (`PricingRule.apply`) is unchanged. Strategy + Adapter patterns shine.

---

## Q14. "How do you avoid duplicate bookings on retries?"

> `Idempotency-Key` header on `POST /bookings`. Server stores it on the booking row with UNIQUE constraint. On a retry with the same key, we return the original response (200 with the existing booking). Different payload + same key → `409 IDEMPOTENCY_PAYLOAD_MISMATCH`.

---

## Q15. "What are the most common bugs in hotel-booking systems you'd watch for?"

> Three:
>
> 1. **Off-by-one on dates** — confusing check-in vs check-out as inclusive ranges. Convention: book nights `[checkIn, checkOut)`.
> 2. **Timezone mismatches** — `LocalDate` in DB but UTC instants in API. Always document and test.
> 3. **Inventory drift** — bug somewhere causes available > total or available < 0. Constraint helps catch; reconciliation cron alerts.

---

Practice each one. Aim for clear claim → justification → tradeoff in 90s.
