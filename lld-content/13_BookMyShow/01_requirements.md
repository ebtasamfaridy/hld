# 01 · BookMyShow — Requirements

## Functional requirements

### Core
- Browse: cities → movies → theatres → shows.
- Show details: screen layout, seat categories (Recliner / Premium / Standard), price.
- **Pick seats** (1..N) → seats turn "held" for this user (TTL ~10 minutes).
- Pay → seats become **CONFIRMED** (booked). Generate ticket(s).
- If TTL expires before payment, seats return to `AVAILABLE`.
- View **My bookings**: upcoming and past.
- **Cancel** booking (configurable refund window).

### Concurrency contract
- A given seat for a given show is sold **at most once**. No double-booking under any concurrency.
- A user can hold seats for ≤ TTL; multiple users cannot hold the same seat.
- Once payment succeeds, the booking is irrevocable from the seat-availability perspective (a refund happens but the seat doesn't auto-re-list — operator may release it).

### Pricing
- Per-show base price by seat category.
- **Surge** by show occupancy (e.g., > 70 % occupancy → 1.2× price for new bookings).
- Convenience fee per booking.
- Promo codes / discounts (light).

### Out of scope
- Logistics (printing tickets at the theatre): we expose a QR.
- Seat-level recommendations (group seating).
- Multi-language UX.

### Extensions
- Group seating recommendations (book 4 contiguous seats).
- Subscriptions (movie pass).
- Theatre operator dashboards (occupancy, revenue).
- Region-aware caching (a show in Mumbai vs Delhi).

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Show open spike | 100 K bookings in first 5 minutes | Big releases |
| Hold latency | < 200 ms | UX |
| Confirm latency | < 1 s (incl payment) | UX |
| No double-booking | strict | foundational |
| Availability | 99.95 % | Revenue |
| Stale seat clear-out | within ~1 min of TTL expiry | Avoid phantom holds |

## Actors

```
Customer             - browses, holds seats, pays
TheatreOperator      - configures shows, screens, seats
ContentTeam          - movies, posters
PaymentGateway       - external (cards / UPI / wallet)
NotificationService  - SMS/email after confirm
```

## Edge cases

| Case | Handling |
| --- | --- |
| Two users tap the same seat at the same instant | Atomic `INSERT … ON CONFLICT` for hold; loser sees "seat just got picked" |
| Payment in flight, TTL expires | Payment must be authorized before TTL expiry. If gateway is slow, we extend hold once (idempotent) |
| Payment fails after TTL expired | Refund (if charged); seat already released — nothing to release |
| User refreshes mid-hold | Hold lookup by `holdId` returns same hold; UX continues |
| Show starts, user is mid-checkout | Block confirm; refund any payment |
| Operator cancels show | All confirmed bookings refunded; admin-driven |
| Pricing changes mid-checkout | Hold quote is locked at hold time |
| Promo code used twice by same user | Reject second use |

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Hold + confirm (Redis SETNX, Postgres TX) | ✓ | |
| Pricing strategy with surge | ✓ | |
| Payment gateway adapter | ✓ | |
| Multi-region caches | | ✓ |
| Group seating recommendations | | ✓ |
| Subscriptions | | ✓ |
| Operator dashboards | | ✓ |

## Output

```
Actors:    Customer, Operator, ContentTeam, PaymentGateway, NotificationService
Core FR:   browse → pick → hold (TTL) → pay → confirm; cancel; my-bookings
NFR:       100K bookings/5min, no double-book, <200ms hold, <1s confirm
Edge:      simultaneous picks, payment-vs-TTL race, show cancelation
```
