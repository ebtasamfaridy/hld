# 12 · BookMyShow — Machine Coding Skeleton

In-memory implementation of the hold / confirm flow.

```
src/main/java/com/bookmyshow/
├── domain/        Movie, Theatre, Screen, Seat, Show, SeatId, SeatCategory,
│                  Hold, Booking, BookedSeat, PriceQuote, Money, ShowStatus,
│                  ConfirmResult (sealed), HoldResult (sealed)
├── inventory/     SeatLock + InMemorySeatLock (mimics Redis SETNX with TTL)
├── pricing/       PricingPolicy + BasePlusSurgePricing
├── payment/       PaymentService + StubPaymentService (idempotent)
├── repository/    ShowRepository + InMemoryShowRepository,
│                  HoldRepository + InMemoryHoldRepository,
│                  BookingRepository + InMemoryBookingRepository
├── booking/       BookingService
├── listener/      ConsoleLogger (for events)
└── Main.java
```

## Demo

1. Build a 5×5 screen, one show.
2. User A holds A1, A2 → success, quote ₹700.
3. User B tries A2, A3 → 409 conflict on A2; A3 not held.
4. User A confirms with payment → BookingConfirmed.
5. User C holds A3 → success.
6. User C waits past TTL → next confirm returns Expired.
