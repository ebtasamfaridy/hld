# 04 · BookMyShow — Domain Model

## Aggregates

```text
Movie (root)                     # catalog
├── id, title, language, duration, rating, posters

Theatre (root)                   # catalog
├── id, name, city, geo, screens[]
└── Screen
    ├── id, name, layout (rows × cols)
    └── seats[] (id, row, col, category)

Show (root)                      # the schedule + inventory
├── id, movie, screen, startsAt, durationMin
├── pricing: PricingPolicy
├── seatStatus: Map<SeatId, SeatStatus>     # AVAILABLE | HELD | BOOKED | BLOCKED
├── version

Hold (root)                      # transient, ~10 min TTL
├── id (UUID)
├── userId
├── showId
├── seats: Set<SeatId>
├── price quote (locked at hold time)
├── createdAt, expiresAt
└── status: HELD | CONFIRMED | EXPIRED | CANCELLED

Booking (root)                   # confirmed
├── id, userId, showId, holdId
├── seats: List<BookedSeat>
├── totalAmount, paymentRef
├── createdAt, status: CONFIRMED | CANCELLED | REFUNDED
└── version

BookedSeat (record)
├── seatId, category, price (at-book-time)
```

## Why split Show seat state from Booking

- The seat-state map is **hot** (read on every layout refresh) — keep it in Redis or a denormalized table.
- The `bookings` table is the durable truth for what's actually been sold.
- Reconciliation: `bookings` → `show.seatStatus.BOOKED`. If they ever disagree, `bookings` wins.

## Seat compatibility & pricing

Pricing is a strategy keyed off `SeatCategory`:

```java
public interface PricingPolicy {
    Money quote(Show show, List<Seat> seats, Instant now);
}

public final class BasePlusSurgePricing implements PricingPolicy {
    private final Map<SeatCategory, Money> base;
    private final double surgeAt70;        // multiplier when occupancy > 70%
    private final double surgeAt90;        // multiplier when occupancy > 90%
    private final Money convenienceFee;
}
```

Quote price is **locked at hold time** — written into the `holds` row. Confirm uses the held quote, not a fresh recomputation.

## Hold semantics

A `Hold` reserves seats for `expiresAt` minutes. While `HELD`:
- The user can `confirm` (with payment).
- The user can `cancel` (release seats).
- Time can expire (auto-release via Redis TTL + audit row eventually marked `EXPIRED`).

```java
public final class Hold {
    public boolean isAlive(Instant now) {
        return status == HELD && now.isBefore(expiresAt);
    }
    public Hold confirm(PaymentRef p) {
        require(status == HELD, "not held");
        return new Hold(/* status = CONFIRMED, paymentRef = p */);
    }
}
```

## Confirm — the hard part

The state transition `HELD → CONFIRMED` involves four mutations:
1. Insert `bookings` row.
2. Insert `booking_seats` rows (with PK enforcement).
3. Update `holds` to `CONFIRMED`.
4. Update or invalidate the show's seat status cache.

**One Postgres transaction** covers (1)–(3). Cache invalidation (4) is a write to Redis after commit; failure is recoverable (next read repopulates).

The unique constraint `PRIMARY KEY (show_id, seat_id)` on `booking_seats` is the safety net.

## Domain events

```
- ShowOpened(showId)                  # admin-driven
- HoldCreated(holdId, showId, seats, expiresAt)
- HoldExpired(holdId)                 # auto via TTL
- BookingConfirmed(bookingId, showId, seats, total, ts)
- BookingCancelled(bookingId, refundAmount)
- ShowCancelled(showId)               # admin-driven; downstream refunds all
```

## Output

```
Aggregates:    Movie, Theatre/Screen, Show (with seat state), Hold, Booking
Strategy:      PricingPolicy
Concurrency:   Redis SETNX for hold; Postgres TX + PK for confirm
TTL:           Redis-managed; no cron
Quote:         locked at hold time, persisted on hold row
```
