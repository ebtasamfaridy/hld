# 12 · Parking Lot — Machine Coding Skeleton

```
src/main/java/com/parking/
├── domain/         VehicleType, SpotType, SpotId, TicketId, Vehicle, Spot, Ticket, Money, Compatibility
├── allocation/     AllocationStrategy + NearestEntrance / Balanced impls
├── pricing/        PricingStrategy + FlatHourly / Tiered / FreeFirstWindow
├── repository/     SpotRepository (in-memory), TicketRepository (in-memory)
├── gateway/        EntryGate, ExitGate, EntryResult (sealed)
├── listener/       LotListener, ConsoleLogger
├── ParkingLot.java
└── Main.java
```

## Demo

1. Build a 2-floor lot: floor 1 = 5 BIKE + 5 COMPACT + 5 LARGE; floor 2 = 5 LARGE + 5 EV.
2. Bike, Car, Truck, EV_CAR enter through different gates.
3. Watch allocator pick the right spot per type.
4. After 90 minutes, exit and pay (FlatHourlyPricing).
5. Try a TRUCK when no LARGE is free → LotFull.
