# 07 · Food Delivery — Class Diagrams

## Domain layer (aggregates)

```mermaid
classDiagram
  class Order {
    -UUID id
    -UUID customerId
    -UUID restaurantId
    -OrderStatus status
    -List~OrderItem~ items
    -DeliverySnapshot delivery
    -PriceBreakdown price
    -UUID assignmentId
    -long version
    +confirm()
    +reject(String reason)
    +cancel(CancelReason reason)
    +markPreparing()
    +markReady()
    +markOutForDelivery(UUID assignmentId)
    +markDelivered()
    +canCancel() bool
  }

  class OrderItem {
    -UUID id
    -UUID menuItemId
    -String nameSnap
    -int quantity
    -Money unitPriceSnap
    +lineTotal() Money
  }

  class PriceBreakdown { <<value>> }
  class DeliverySnapshot { <<value>> }

  class OrderStatus {
    <<enumeration>>
    PLACED CONFIRMED PREPARING READY_FOR_PICKUP
    OUT_FOR_DELIVERY DELIVERED CANCELLED REJECTED
  }

  Order "1" *-- "1..*" OrderItem
  Order ..> PriceBreakdown
  Order ..> DeliverySnapshot
  Order ..> OrderStatus
```

```mermaid
classDiagram
  class Driver {
    -UUID id
    -String name
    -DriverStatus status
    -double rating
    -String city
    -long version
    +goOnline()
    +goOffline()
    +reserveForOffer()
    +releaseFromOffer()
    +markBusy()
    +markIdle()
  }

  class DriverStatus {
    <<enumeration>>
    OFFLINE IDLE OFFER_PENDING BUSY
  }

  class DeliveryAssignment {
    -UUID id
    -UUID orderId
    -UUID driverId
    -AssignmentStatus status
    -Instant offeredAt
    -Instant respondedAt
    -Instant pickedUpAt
    -Instant deliveredAt
    -Instant expiresAt
    -long version
    +accept()
    +reject(String reason)
    +expire()
    +pickedUp()
    +delivered()
  }

  class AssignmentStatus {
    <<enumeration>>
    OFFERED ACCEPTED REJECTED EXPIRED PICKED_UP DELIVERED CANCELLED
  }

  Driver ..> DriverStatus
  DeliveryAssignment ..> AssignmentStatus
```

---

## Application services

```mermaid
classDiagram
  class OrderService {
    -OrderRepository orderRepo
    -CartValidator validator
    -PricingService pricing
    -InventoryService inventory
    -PaymentService payment
    -IdempotencyStore idem
    -EventPublisher events
    +placeOrder(PlaceOrderCommand) Order
    +cancel(UUID id, CancelReason reason) Order
    +confirm(UUID id) Order
    +reject(UUID id, String reason) Order
  }

  class PricingService {
    -List~PricingRule~ rules
    +compute(Cart) PriceBreakdown
  }

  class DispatchService {
    -DriverFinder finder
    -ScoringStrategy scorer
    -OfferSender sender
    -DriverRepository drivers
    -AssignmentRepository assignments
    +dispatch(Order) Optional~DeliveryAssignment~
  }

  class TrackingService {
    -RedisGeo redis
    -WebSocketRegistry ws
    +pushDriverLocation(UUID driverId, Location loc)
    +subscribe(UUID orderId, WebSocketSession s)
  }

  OrderService --> PricingService
  OrderService --> InventoryService
  OrderService --> PaymentService
  OrderService --> EventPublisher
  DispatchService --> DriverFinder
  DispatchService --> ScoringStrategy
```

---

## Pluggable strategies

```mermaid
classDiagram
  class PricingRule {
    <<interface>>
    +apply(Cart cart, PriceBreakdownBuilder b)
  }

  class TaxRule
  class DeliveryFeeRule
  class SurgeRule
  class PromoCodeRule

  PricingRule <|.. TaxRule
  PricingRule <|.. DeliveryFeeRule
  PricingRule <|.. SurgeRule
  PricingRule <|.. PromoCodeRule

  class ScoringStrategy {
    <<interface>>
    +score(Driver d, Order o, Location pickup) double
  }

  class NearestFirstScoring
  class WeightedScoring
  class BatchAwareScoring

  ScoringStrategy <|.. NearestFirstScoring
  ScoringStrategy <|.. WeightedScoring
  ScoringStrategy <|.. BatchAwareScoring

  class DriverFinder {
    <<interface>>
    +findCandidates(Location, double radiusKm, int limit) List~Driver~
  }

  class RedisGeoDriverFinder
  class JpaDriverFinder

  DriverFinder <|.. RedisGeoDriverFinder
  DriverFinder <|.. JpaDriverFinder
```

The `ScoringStrategy` is what makes "nearest" vs "highest-rated" vs "batch-aware" pluggable. We discuss it in detail in `10_design_patterns.md`.

---

## Repositories

```mermaid
classDiagram
  class OrderRepository {
    <<interface>>
    +findById(UUID) Optional~Order~
    +findByIdempotencyKey(String) Optional~Order~
    +save(Order) Order
    +findActiveByCustomer(UUID) List~Order~
    +findActiveByRestaurant(UUID) List~Order~
  }

  class JpaOrderRepository
  class InMemoryOrderRepository

  OrderRepository <|.. JpaOrderRepository
  OrderRepository <|.. InMemoryOrderRepository

  class DriverRepository {
    <<interface>>
    +findById(UUID) Optional~Driver~
    +findIdleInCity(String) List~Driver~
    +save(Driver) Driver
  }

  DriverRepository <|.. JpaDriverRepository
```

---

## Events

```mermaid
classDiagram
  class DomainEvent { <<interface>> }
  class OrderPlaced { -UUID orderId; -Instant at; }
  class OrderConfirmed
  class OrderRejected
  class OrderCancelled
  class OrderReadyForPickup
  class OrderOutForDelivery
  class OrderDelivered
  class DriverOnline
  class DriverOffline
  class DeliveryOffered
  class DeliveryAccepted

  DomainEvent <|.. OrderPlaced
  DomainEvent <|.. OrderConfirmed
  DomainEvent <|.. OrderCancelled
  DomainEvent <|.. OrderReadyForPickup
  DomainEvent <|.. OrderOutForDelivery
  DomainEvent <|.. OrderDelivered
  DomainEvent <|.. DriverOnline
  DomainEvent <|.. DriverOffline
  DomainEvent <|.. DeliveryOffered
  DomainEvent <|.. DeliveryAccepted

  class EventPublisher {
    <<interface>>
    +publish(DomainEvent)
  }

  class OutboxEventPublisher
  class KafkaEventPublisher
  class InMemoryEventPublisher

  EventPublisher <|.. OutboxEventPublisher
  EventPublisher <|.. KafkaEventPublisher
  EventPublisher <|.. InMemoryEventPublisher
```

---

## Layering (clean architecture)

```mermaid
flowchart LR
  subgraph api[api]
    OrderController
    DriverController
    RestaurantController
  end

  subgraph app[application]
    OrderService
    DispatchService
    TrackingService
    PricingService
  end

  subgraph dom[domain]
    Order
    Driver
    DeliveryAssignment
    PricingRule_int[PricingRule]
    OrderRepository_int[OrderRepository]
    EventPublisher_int[EventPublisher]
  end

  subgraph infra[infra]
    JpaOrderRepository
    KafkaEventPublisher
    RedisGeoDriverFinder
    StripeGateway
  end

  api --> app
  app --> dom
  infra --> dom
  app --> infra
```

`infra` implements interfaces declared in `dom`. `app` orchestrates. `api` exposes HTTP.

> The arrow that **must not exist** is from `dom` to `infra`. If you ever see `import com.example.infra.*` inside a domain class, you have a leak.

---

## Key takeaways

- **Order** and **DeliveryAssignment** are separate aggregates; they communicate via events.
- **Strategies** plug in at every place an algorithm varies (pricing, scoring, finding).
- **Repositories** are interfaces in `domain`; implementations live in `infra`.
- **EventPublisher** is the seam between sync transaction and async fanout (Outbox pattern).
- The whole codebase respects the **dependency rule** of clean architecture.
