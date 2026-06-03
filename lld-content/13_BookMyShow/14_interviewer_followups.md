# 14 · BookMyShow — Interviewer Follow-ups

## Q1. "How do you guarantee no double-booking?"

Two layers:
1. **Hold layer (Redis)**: `SET NX EX` on `hold:{showId}:{seatId}`. Atomic; only one writer wins.
2. **Confirm layer (Postgres)**: `PRIMARY KEY (show_id, seat_id)` on `booking_seats`. Even if Redis fails, the unique constraint prevents two confirmed bookings for the same seat.

Defense in depth. Both layers are in-place; if either is enough on its own, we keep the other as a safety net.

---

## Q2. "Why not just use Postgres row-locks for hold?"

`SELECT FOR UPDATE` works but holds row locks for the duration of the user's checkout (10 minutes). At show open, hundreds of users compete for adjacent seats. Postgres serializes them — terrible UX.

Redis SETNX is ~100× cheaper, and the TTL primitive expires holds automatically without a cron.

---

## Q3. "User starts checkout, payment is slow, hold expires mid-payment. What happens?"

Two scenarios:

**A**: Payment hasn't been charged yet. We check `hold.expires > now()` before charging; if expired, return 410 to the user, do not charge.

**B**: Payment already authorized but TTL elapsed. We charge then check the hold; if expired, refund. Cost: one round-trip with the gateway. Mitigation: extend hold TTL when payment starts (idempotent, capped).

---

## Q4. "Pricing change while user is in checkout."

The quote is locked at hold time and persisted in `holds.quote_amount_minor`. Confirm uses that value. Surge can move during the session, but the user pays what they were quoted.

---

## Q5. "Concurrent users grabbing different subsets of overlapping seats."

User A holds {A1, A2}. User B holds {A2, A3}.

The Redis SETNX for `A1` succeeds for A. SETNX for `A2` succeeds for A.

User B's SETNX for `A2` returns false; B is told the conflict is on A2; we **release B's SETNX on `A3`** (the one B already won) so B can retry with `{A4, A5}` cleanly.

The all-or-nothing rollback is critical — partial holds leak inventory.

---

## Q6. "Show-open thundering herd. 100 K users hit `GET /shows/{id}/seats` simultaneously."

- CDN with short TTL on layout.
- Redis cache with single-flight: `SETNX layout-loading:{showId}` ensures only one origin fetch.
- Pre-warm the cache 30s before the show opens.
- Layout has version; reads use `layout:show:{id}:v{version}` so cache invalidation is automatic when seats change.

The DB never sees a stampede.

---

## Q7. "Postgres goes down during confirm. What happens?"

The Postgres TX fails; payment was already charged (or is in flight). We:
1. Don't book anything (TX rolled back).
2. Refund the payment via the idempotency-keyed `refund` call.
3. Release the Redis seat locks.
4. Return 5xx to the user; they can retry.

Postgres failure → no booking + automatic refund. The worst case is the user momentarily sees their card charged then refunded.

---

## Q8. "Kafka down. How do downstream services find out about a confirmed booking?"

Outbox pattern. The booking confirm TX writes to `bookings + booking_seats + outbox` atomically. A separate publisher polls the outbox and pushes to Kafka. If Kafka is down, outbox accumulates; once Kafka recovers, the publisher catches up.

Consumers must be **idempotent** (keyed on `booking_id` or event UUID) to tolerate at-least-once delivery.

---

## Q9. "Can two users with different account types hold the same seat?"

No. The hold key is `hold:{showId}:{seatId}` — independent of user. Whoever wins SETNX owns the hold for the duration. The lock value is the user_id, used only for the release-with-ownership-check.

---

## Q10. "User holds 4 seats, pays for 4. Then cancels just 2."

Default: cancel-all-or-none. Tickets are usually a unit. If the product wants partial cancel, we'd model each seat as a sub-booking. That's a separate aggregate — V2 extension.

---

## Q11. "How do you handle hold lifecycle for analytics?"

Redis TTL handles automatic expiry. The `holds` row in Postgres needs `status='EXPIRED'` for accurate reporting. Two strategies:
1. **Lazy update**: when someone tries to confirm an expired hold, mark it on-read.
2. **Sweeper job**: low-priority cron updates `holds WHERE status='HELD' AND expires_at < now()` every 5 minutes. Doesn't affect correctness; only analytics.

V1 uses lazy update; V2 adds the sweeper.

---

## Q12. "Most subtle bug a candidate writes?"

Three big ones:
1. **Charging before checking hold expiry.** Always: load hold → verify alive → charge.
2. **Not rolling back partially-acquired locks on conflict.** Leaks inventory.
3. **Releasing a lock based on key existence instead of ownership.** A user can release another user's lock if you only check existence.

---

## Q13. "Two requests to confirm the same hold (double-tap)."

Idempotency-Key required. Server caches the response keyed on `(user, hold_id, idempotency_key)`. Second call returns the original response.

The DB confirm uses `UPDATE holds SET status='CONFIRMED' WHERE id=$1 AND status='HELD'`; second TX sees `status='CONFIRMED'` already, updates 0 rows, treats as already-done.

---

## Q14. "Show cancellation — how do you bulk-refund?"

Admin endpoint marks show CANCELLED. Worker process iterates `bookings WHERE show_id=$1 AND status='CONFIRMED'`; for each, marks status CANCELLED, publishes `BookingCancelled` event. Refund worker consumes events, processes per-payment-gateway refund with idempotency keys.

For 1000s of bookings on a popular show, the worker runs in batches with rate limits to the gateway.

---

## Q15. "How do you test this?"

- **Property-based concurrency**: spawn 100 threads attempting to hold/confirm overlapping seats; assert (a) total successful bookings = 1 per seat; (b) every successful confirm has a unique paymentRef; (c) all denials have a documented reason.
- **TTL tests**: with virtual clock, advance time past TTL; assert hold expires and seat is reclaimable.
- **Idempotency tests**: replay the same confirm request 5×; assert exactly one booking, one charge.
- **Failure tests**: payment failure mid-flow → assert seats released; Postgres failure → assert payment refunded.

---

## Output

```
Drill questions covered:
- Two-layer no-double-book guarantee (Redis + Postgres PK)
- Why not Postgres-only locks (latency)
- TTL vs payment race
- Pricing locked at hold time
- All-or-nothing hold acquisition
- Show-open stampede mitigation
- Postgres outage semantics (refund + release)
- Kafka outage via outbox
- Lock ownership (release by user only)
- Hold lifecycle for analytics
- Common bugs (early charge, partial-acquire leak, ownership check)
- Idempotency on confirm
- Show cancellation flow
- Testing strategy
```
