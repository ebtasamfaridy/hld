# 12 · Machine Coding Skeleton — Hotel Booking

A production-flavored Java skeleton.

## Layout

```
src/main/java/com/hotelbooking
├── Main.java
├── domain/                ← Hotel, RoomType, RoomInventory, Booking, BookingStatus
├── repository/            ← In-memory inventory + booking repos with atomic decrement
├── service/               ← BookingService, InventoryService
├── pricing/               ← PricingService, PricingRule
├── inventory/             ← inventory primitives
└── api/                   ← controller (CLI)
```

## Demo flow

1. Seed hotel + room type + 30 days of inventory.
2. Customer books 3 nights — atomic decrement.
3. Idempotent retry — same booking returned.
4. Customer cancels → inventory released, refund per policy.
5. Concurrent booking attempt for same nights — only one succeeds.
6. Modify booking — release old, reserve new.

## Highlights

- `RoomInventoryRepository.decrement()` simulates SQL CAS.
- `Booking` aggregate with state guards.
- `CancellationPolicy` strategy injected into Booking.
- `PricingRule` strategy chain in `PricingService`.
