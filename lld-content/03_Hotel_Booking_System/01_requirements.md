# 01 · Hotel Booking — Requirements

## Problem statement

Design a hotel booking platform like Booking.com / OYO / Marriott where a user searches for hotels by destination + dates + guests, browses rooms, books one or more, pays, and later checks in / checks out.

We design the **backend**.

---

## Functional requirements

### Core (in scope)

**Guest-facing**
- Search hotels by city + check-in/out + occupancy + filters (price, rating, amenities).
- View hotel details, room types, photos, reviews summary.
- See real-time availability and price for a date range.
- Book one or more rooms in a single hotel; pay (full or deposit).
- Modify booking (change dates / room count) within policy.
- Cancel booking with policy-based refund.
- Receive confirmation, reminders, check-in instructions.
- Submit a review post-stay.

**Hotel-facing**
- Onboard property (rooms, photos, amenities, policies).
- Manage daily inventory and pricing per room type per date.
- Receive booking notifications.
- Mark guest as checked-in / checked-out.
- Block dates (maintenance).

**Platform-facing**
- Prevent double booking under contention.
- Compute price (base + seasonal + occupancy-based + tax + fees - discount).
- Apply cancellation policy.
- Pay-out to hotels on a settlement schedule.
- Run availability calendar refresh (sometimes inventory comes from PMS).

### Extensions (acknowledged, not built today)

- Reviews and ratings (full).
- Loyalty programs.
- Group bookings (10+ rooms with workflow).
- Dynamic / ML-driven pricing engines.
- Channel manager integrations (Booking.com vs Agoda dual-listing).
- Wishlist / saved searches.
- Multi-currency & FX.
- Refer-a-friend.

### Out of scope

- Mobile apps.
- Payment gateway internals.
- KYC for hotel onboarding.
- Reviews moderation.

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Search p99 | < 300 ms | Conversion |
| Booking p99 | < 500 ms | Money-critical, single transaction |
| Availability accuracy | strong consistency at booking | Avoid oversell |
| Search consistency | eventual (1-2 min lag ok) | Cacheable |
| Throughput | 5 K bookings/sec peak | Black-Friday / sale peaks |
| Availability | 99.95 % | Trust |
| Audit | every booking transition logged | Disputes |

---

## Actors

```
Guest               - searches, books, modifies, cancels, checks-in
HotelOwner          - manages property + inventory
Admin / Support     - disputes, refunds, suspensions
PaymentGateway      - external; charge / refund
NotificationService - email / SMS / push
PricingService      - internal; computes nightly prices
SearchIndex         - read store for hotel search
CalendarService     - manages inventory by date
```

---

## Edge cases

| Case | Handling |
| --- | --- |
| Two guests book the last room | One wins via DB CAS; the other gets `409 NOT_AVAILABLE` |
| Hotel marks dates blocked while booked | Reject if active bookings span; or warn admin |
| Guest cancels 1 night of multi-night booking | Modify booking; recompute fee |
| Guest changes dates that span fully booked night | Reject with available alternatives |
| Payment captured but DB write failed | Outbox + reconciliation |
| Multi-room booking; one room becomes unavailable mid-checkout | Reject all (atomic) or partial confirm with user consent |
| Same booking cancellation called twice | Idempotent cancel |
| Currency mismatch between guest and hotel | Server snapshots both currencies + FX rate |
| Hotel goes inactive after booking | Booking remains valid; hotel still must honor or guest gets full refund |
| Blackout dates (event/holiday) | Pricing rule + inventory zero |

---

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Single-hotel booking | ✓ | |
| Date-range inventory | ✓ | |
| Cancellation policies (3 standard) | ✓ | |
| Multi-room single hotel | ✓ | |
| Search w/ filters | ✓ | |
| Reviews | partial | ✓ |
| Loyalty | | ✓ |
| Group bookings | | ✓ |
| Dynamic pricing (ML) | | ✓ |
| Multi-currency | | ✓ |

---

## Output

```
Actors:        Guest, HotelOwner, Admin, Payment, Notif, Pricing, Search, Calendar
Core FR:       search, view, book, modify, cancel, check-in/out, pay, review
NFR:           search p99 300ms, booking p99 500ms, strong consistency on inventory
Out of Scope:  payment internals, mobile, KYC
Extensions:    loyalty, groups, dynamic pricing, multi-currency
```
