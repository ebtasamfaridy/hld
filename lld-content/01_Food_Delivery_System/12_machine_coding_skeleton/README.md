# 12 · Machine Coding Skeleton — Food Delivery

A production-flavored Java skeleton you can compile, run, and extend in a 90-minute machine-coding round.

## Layout

```
src/main/java/com/fooddelivery
├── Main.java                          ← composition root + demo
├── api/                               ← controllers (CLI here)
├── domain/                            ← entities, value objects, enums
├── service/                           ← application services
├── repository/                        ← in-memory implementations
├── dispatch/                          ← matching strategies
├── concurrency/                       ← idempotency store, locks
└── config/
```

## Run

```bash
javac -d out $(find src/main/java -name "*.java")
java  -cp out com.fooddelivery.Main
```

## What's inside

- `Order`, `OrderItem`, `Driver`, `DeliveryAssignment` aggregates.
- `OrderService` with idempotency + optimistic locking via in-memory CAS.
- `PricingService` with `PricingRule` strategy.
- `DispatchService` with `ScoringStrategy` strategy.
- Simple `EventBus` (Observer).
- `OrderStateMachine` with transition map.
- `Money` value object.

## Demo flow in `Main`

1. Seed restaurants, items, drivers.
2. Place an order (idempotent retry shown).
3. Restaurant accepts.
4. Dispatcher offers and driver accepts.
5. Driver picks up, delivers.
6. Cancel attempt at delivered → fails as expected.

This is the same flow you'd implement live.
