# 13 · BookMyShow — Extensions & Tradeoffs

## Extensions

### 1. Group seating recommendations
Algorithm: maximum contiguous run of `n` available seats per row. Surface "best 4 seats together" UX. Not part of booking; a search/recommend function.

### 2. Pricing — Promo codes / discounts
Decorate `PricingPolicy` with `PromoPricing`. Validates code, discounts the inner quote. Promo codes per-user one-time-use enforced via DB unique constraint.

### 3. Subscriptions (movie pass)
A subscription confers N free bookings/month. Treat as another `PricingPolicy` decorator: `SubscriptionPricing(inner)` zeros base price for subscribers; convenience fee still applies.

### 4. Seat re-listing on cancel
When booking is cancelled, optionally re-list seats. Mark them AVAILABLE and increment show layout version → next refresh shows them.

### 5. Show cancellation
Bulk admin path. UPDATE bookings → CANCELLED; emit events; refund worker processes.

### 6. Seat blocks (reserved for cinema staff, sponsors)
Static `BLOCKED` flag on seat. Allocator skips. Admin sets/clears.

### 7. Multi-currency
Money already abstracts. Per-show currency. Payment gateway must handle.

### 8. Loyalty points
Earn points per booking; redeem via decorator pricing. Points tracked in user service.

### 9. Notifications
Kafka `booking.events` consumed by notification service: SMS, email, push.

### 10. Anti-bot
Rate limit holds per IP/user; CAPTCHA after suspicious patterns.

## Tradeoffs

### Redis SETNX vs Postgres-only locking

| Criterion | Redis SETNX | Postgres-only |
| --- | --- | --- |
| Latency | <1 ms | ~5 ms |
| TTL | automatic | manual cron / NOT EXISTS check |
| Throughput | 100 K/s | 5–10 K/s |
| Concurrency | very high | row-lock contention |
| Decision | **Redis SETNX + Postgres PK as defense in depth** ✓ |

### Quote at hold-time vs confirm-time

| Criterion | Hold-time | Confirm-time |
| --- | --- | --- |
| User sees stable price | yes | no |
| Surge can apply mid-checkout | no | yes (operator-friendly) |
| UX | predictable | confusing |
| Decision | **Hold-time** ✓ |

### Outbox pattern vs direct Kafka publish

| Criterion | Outbox | Direct |
| --- | --- | --- |
| Atomic with DB | yes | no |
| Latency | small lag | immediate |
| Code complexity | medium | low |
| Decision | **Outbox** ✓ for revenue events |

### Auto-cancel on TTL vs manual cron

Redis TTL handles 95 %. The remaining 5 % is the row in `holds` that needs `status='EXPIRED'` for analytics. Two approaches:
- **Lazy update on read**: when someone hits `GET /holds/{id}` after expiry, mark expired. ✓ V1.
- **Cron sweeper**: low-priority job marks expired rows. V2 if analytics need fresher data.

### Inventory model: Row-per-seat vs Set-membership

| Criterion | Row-per-seat | Set membership (Redis) |
| --- | --- | --- |
| Storage | O(seats) per show | O(held seats) |
| Query "is X available" | indexed lookup | `SISMEMBER` |
| Atomic claim | row-lock or PK | SETNX |
| Decision | **Both**: Postgres for truth, Redis as hot cache. |

## Open questions

- Should we extend hold TTL when the user starts payment? (Yes; cap at 2× original.)
- Can a user hold seats in multiple shows simultaneously? (Yes; per-user soft cap.)
- What's the cancellation refund window policy? (Product call.)
- Should partial refunds be allowed (refund some seats, keep others)? (Usually no; ticket integrity.)

## Output

```
Extensions:    group seating, promos, subscriptions, re-listing, show-cancel,
               blocks, multi-currency, loyalty, notifications, anti-bot
Pre-decided:   Redis SETNX + Postgres PK; quote at hold-time; outbox events;
               lazy expire-marking on read
Open Qs:       TTL extension on payment, multi-show holds, refund windows, partial refunds
```
