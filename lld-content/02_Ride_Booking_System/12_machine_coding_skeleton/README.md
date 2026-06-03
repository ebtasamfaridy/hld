# 12 · Machine Coding Skeleton — Ride Booking

A production-flavored Java skeleton for a ride-booking system, suitable for a 90-min machine coding round.

## Layout

```
src/main/java/com/ridebooking
├── Main.java                          ← composition root + demo
├── domain/                            ← Ride, Driver, value objects, enums
├── repository/                        ← in-memory repos
├── service/                           ← Ride/Pricing/Tracking services
├── matching/                          ← match engine + scoring strategy
├── pricing/                           ← surge + pricing rules
├── state/                             ← Ride state machine (State pattern)
└── api/                               ← controller (CLI)
```

## Demo flow in `Main`

1. Seed riders, drivers, vehicles.
2. Drivers go online.
3. Rider requests ride; matching finds driver.
4. Driver accepts → ARRIVING → ARRIVED.
5. Rider OTP → IN_TRIP.
6. Trip ends → COMPLETED + payment.
7. Cancel attempts at COMPLETED fail.

## Key files

- `Ride.java` — aggregate root with State pattern.
- `Driver.java` — driver aggregate with status transitions.
- `MatchingService.java` — finds and assigns driver, with optimistic CAS.
- `PricingService.java` — composes pricing rules.
- `SurgeService.java` — per-zone factor.
- `ScoringStrategy.java` — pluggable scoring (NearestFirst, Weighted).

This skeleton illustrates the LLD; persistence is in-memory.
