# 07 · Ride Booking — Class Diagrams

## Domain layer

```mermaid
classDiagram
  class Ride {
    -UUID id
    -UUID riderId
    -UUID driverId
    -RideType type
    -RideStatus status
    -Location pickup
    -Location drop
    -FareEstimate estimate
    -FareFinal finalFare
    -Trip trip
    -BigDecimal surgeFactorLocked
    -long version
    +match(UUID driverId)
    +driverArriving()
    +driverArrived()
    +start()
    +end(double km, int minutes)
    +cancelByRider(reason)
    +cancelByDriver(reason)
    +noShow()
    +rate(int stars, Money tip, String comment)
  }

  class Trip {
    -List~PathPoint~ path
    -double distanceKm
    -int durationMinutes
    -Instant startedAt
    -Instant endedAt
  }

  class FareEstimate { <<value>> }
  class FareFinal { <<value>> }
  class Location { <<value>> }
  class Money { <<value>> }
  class PathPoint { <<value>> }

  Ride ..> Trip
  Ride ..> FareEstimate
  Ride ..> FareFinal
  Ride ..> Location
```

```mermaid
classDiagram
  class Driver {
    -UUID id
    -DriverStatus status
    -double rating
    -Vehicle vehicle
    -Location lastLocation
    -long version
    +goOnline(); goOffline()
    +reserveForOffer(); releaseFromOffer()
    +acceptOffer(); startTrip(); endTrip()
  }

  class Vehicle { <<value>> }
  class DriverStatus { <<enumeration>> }
  Driver ..> Vehicle
  Driver ..> DriverStatus
```

---

## Application services

```mermaid
classDiagram
  class RideService {
    -RideRepository rides
    -PricingService pricing
    -PaymentService payment
    -EventPublisher events
    -IdempotencyStore idem
    +request(RequestRideCommand)
    +cancel(UUID, CancelReason)
    +start(UUID)
    +end(UUID, EndRideCommand)
    +rate(UUID, RateCommand)
  }

  class PricingService {
    -SurgeService surge
    -List~PricingRule~ rules
    +estimate(EstimateInput)
    +finalize(Trip, BigDecimal surgeFactorLocked)
  }

  class SurgeService {
    -RedisClient redis
    -SurgeAlgorithm algo
    +factor(geohash, RideType)
    +recompute()      // background job
  }

  class MatchingService {
    -DriverFinder finder
    -ScoringStrategy scorer
    -OfferSender sender
    -DriverRepository drivers
    +match(Ride r)
  }

  class TrackingService {
    +pushLocation(driverId, Location)
    +subscribeRide(rideId, WSSession)
  }

  class SafetyService {
    +sos(rideId, kind)
    +anomalyCheck(Ride r, Location current)
  }

  RideService --> PricingService
  RideService --> PaymentService
  PricingService --> SurgeService
  MatchingService --> DriverFinder
  MatchingService --> ScoringStrategy
```

---

## Strategies

```mermaid
classDiagram
  class PricingRule {
    <<interface>>
    +apply(Trip, FareBuilder)
  }
  class BaseFareRule
  class DistanceTimeRule
  class SurgeRule
  class PlatformFeeRule
  class TaxRule
  class TipRule

  PricingRule <|.. BaseFareRule
  PricingRule <|.. DistanceTimeRule
  PricingRule <|.. SurgeRule
  PricingRule <|.. PlatformFeeRule
  PricingRule <|.. TaxRule
  PricingRule <|.. TipRule

  class ScoringStrategy {
    <<interface>>
    +score(Driver, Ride, Location pickup) double
  }
  class NearestFirstScoring
  class ETAFirstScoring
  class FairnessScoring
  ScoringStrategy <|.. NearestFirstScoring
  ScoringStrategy <|.. ETAFirstScoring
  ScoringStrategy <|.. FairnessScoring

  class SurgeAlgorithm {
    <<interface>>
    +compute(int idleDrivers, int pendingRides, double historicalBaseline) BigDecimal
  }
  class DemandSupplyAlgorithm
  class MLAlgorithm
  SurgeAlgorithm <|.. DemandSupplyAlgorithm
  SurgeAlgorithm <|.. MLAlgorithm

  class CancellationPolicy {
    <<interface>>
    +feeFor(Ride r, CancelActor actor) Money
  }
  class IndiaCancellationPolicy
  class USCancellationPolicy
  CancellationPolicy <|.. IndiaCancellationPolicy
  CancellationPolicy <|.. USCancellationPolicy
```

---

## Repositories

```mermaid
classDiagram
  class RideRepository {
    <<interface>>
    +findById(UUID)
    +save(Ride)
    +findByIdempotencyKey(String)
    +findStuckRequests(Duration)
  }
  class JpaRideRepository
  class InMemoryRideRepository
  RideRepository <|.. JpaRideRepository
  RideRepository <|.. InMemoryRideRepository

  class DriverRepository {
    <<interface>>
    +findById(UUID)
    +save(Driver)
    +findByCityAndStatus(String, DriverStatus)
  }
  class JpaDriverRepository
  DriverRepository <|.. JpaDriverRepository

  class DriverFinder {
    <<interface>>
    +findCandidates(Location, RideType, double radiusKm, int limit) List~Driver~
  }
  class RedisGeoDriverFinder
  class JpaDriverFinder
  DriverFinder <|.. RedisGeoDriverFinder
  DriverFinder <|.. JpaDriverFinder
```

---

## State pattern (Ride)

For `Ride.cancelByRider()`:

```mermaid
classDiagram
  class RideState {
    <<interface>>
    +cancelByRider(Ride, reason)
    +cancelByDriver(Ride, reason)
    +start(Ride)
    +end(Ride, km, min)
  }
  class RequestedState
  class MatchedState
  class ArrivingState
  class ArrivedState
  class InTripState
  class CompletedState
  class CancelledState
  class NoShowState

  RideState <|.. RequestedState
  RideState <|.. MatchedState
  RideState <|.. ArrivingState
  RideState <|.. ArrivedState
  RideState <|.. InTripState
  RideState <|.. CompletedState
  RideState <|.. CancelledState
  RideState <|.. NoShowState
```

Each state encodes legal operations. We use full State pattern here (vs enum + map) because cancellation **fee depends on state**, and `start()` validity depends on state. Encapsulating in state classes keeps domain logic local.

---

## Layering

```mermaid
flowchart LR
  subgraph api
    RideController; DriverController; AdminController
  end
  subgraph application
    RideService; MatchingService; PricingService; SurgeService; TrackingService; SafetyService
  end
  subgraph domain
    Ride; Driver; FareEstimate
    RideRepository_int[<<interface>> RideRepository]
  end
  subgraph infra
    JpaRideRepository; RedisGeoDriverFinder; KafkaPublisher; StripeGateway
  end

  api --> application
  application --> domain
  application --> infra
  infra --> domain
```

---

## Take-aways

- **Ride** is the central aggregate; `Trip` is composed inside it.
- **Strategy** for pricing rules, scoring, surge algo, cancellation policy — every variation point.
- **State pattern** for `Ride` because cancellation fees and legal operations are state-dependent.
- All cross-service comms via `EventPublisher` (Outbox + Kafka in production).
