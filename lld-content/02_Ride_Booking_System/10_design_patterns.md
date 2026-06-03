# 10 · Ride Booking — Design Patterns

## 1. Strategy — pricing rules

Same shape as Food Delivery, with rules tuned for rides:

```java
public interface PricingRule { void apply(Trip trip, FareBuilder b); }

class BaseFareRule implements PricingRule { /* flat ₹40 */ }
class DistanceTimeRule implements PricingRule { /* ₹12/km + ₹1.5/min */ }
class SurgeRule implements PricingRule { /* multiplied on subtotal */ }
class PlatformFeeRule implements PricingRule { /* ₹15 fixed */ }
class TaxRule implements PricingRule { /* GST */ }
class TipRule implements PricingRule { /* added at end */ }
class WaitTimeRule implements PricingRule { /* free 3 min, then ₹2/min */ }
```

Different cities / ride types compose different rule lists. Subscription users get a different list (no platform fee).

---

## 2. Strategy — matching scoring

```java
public interface ScoringStrategy {
  double score(Driver d, Ride r, Location pickup);
}

class NearestFirstScoring implements ScoringStrategy { ... }
class ETAFirstScoring implements ScoringStrategy { ... }       // uses Maps API ETA, not raw distance
class FairnessScoring implements ScoringStrategy { ... }       // boost long-idle drivers
class PoolAwareScoring implements ScoringStrategy { ... }      // V2: prefer drivers near multiple requests
class RatingWeightedScoring implements ScoringStrategy { ... } // rider has high rating → premium driver
```

Choose per city, per A/B group, per ride type. A `Map<RideType, ScoringStrategy>` plus a feature flag covers it.

---

## 3. Strategy — surge algorithm

```java
public interface SurgeAlgorithm {
  BigDecimal compute(int idleDrivers, int pendingRides, double historicalBaseline);
}

class RatioBasedSurge implements SurgeAlgorithm {
  public BigDecimal compute(int idle, int pending, double baseline) {
    if (idle == 0 && pending > 0) return new BigDecimal("3.0");
    double ratio = pending / Math.max(idle, 1.0);
    return BigDecimal.valueOf(Math.min(1.0 + ratio * 0.5, 3.0));
  }
}

class MLSurge implements SurgeAlgorithm { /* feature-store-based prediction */ }
```

We compute periodically (every 60 s per zone) and write to Redis with TTL.

---

## 4. State pattern — Ride lifecycle

Justified in `09_state_machines.md`. Each state class encapsulates legal operations and per-state cancellation logic.

---

## 5. Observer / Pub-Sub — events

Outbox + Kafka. Same as Food Delivery.

```
RideRequested → MatchingService
RideMatched   → NotificationService, AnalyticsETL
RideCompleted → SettlementService, NotificationService, RatingPromptScheduler
SOS triggered → SafetyService (priority queue)
```

---

## 6. Repository — persistence

Standard pattern:

```java
public interface RideRepository {
  Optional<Ride> findById(UUID id);
  Ride save(Ride r);
  Optional<Ride> findByIdempotencyKey(String key);
  List<Ride> findStuckRequests(Duration olderThan);
}
```

---

## 7. Decorator — cross-cutting

```java
OrderService rideSvc = new TracingRideService(
                         new MetricsRideService(
                           new LoggingRideService(
                             new CoreRideService(...))));
```

We typically also wrap with a **CircuitBreakerRideService** for downstream calls (Maps, Payment).

---

## 8. Command — every mutation

```java
public record RequestRideCommand(...) {}
public record CancelRideCommand(UUID rideId, CancelActor by, String reason) {}
public record StartRideCommand(UUID rideId, String otp) {}
public record EndRideCommand(UUID rideId, double km, int minutes) {}
```

These flow into `RideService`. Commands enable:
- Saving for replay (debug, audit).
- Validation pipeline (Chain of Responsibility).
- Mocking in tests.

---

## 9. Chain of Responsibility — request validation

```java
List<RequestRideValidator> chain = List.of(
  new AuthValidator(),
  new RateLimitValidator(),
  new PaymentMethodValidator(),
  new EstimateTokenValidator(),     // verify HMAC
  new EligibilityValidator(),       // not banned, not in cool-down
  new GeoBoundaryValidator()        // pickup/drop within service area
);
```

Each runs in order; first failure shorts-circuits.

---

## 10. Factory — DriverFinder

```java
public interface DriverFinder {
  List<Driver> findCandidates(Location p, RideType t, double radiusKm, int limit);
}

public class DriverFinderFactory {
  static DriverFinder of(FinderType type, Deps d) {
    return switch (type) {
      case REDIS_GEO -> new RedisGeoDriverFinder(d.redis());
      case POSTGIS   -> new JpaDriverFinder(d.jdbc());
      case STUB      -> new StubDriverFinder(d.fixedDrivers());
    };
  }
}
```

---

## 11. Adapter — Maps API

We don't tie our domain to Google Maps. We define:

```java
public interface MapsService {
  Distance distance(Location a, Location b);
  Duration eta(Location a, Location b);
  List<Location> route(Location a, Location b);
}

class GoogleMapsAdapter implements MapsService { /* HTTP to Google */ }
class MapboxAdapter implements MapsService { ... }
class StubMapsAdapter implements MapsService { ... }       // for tests
```

Switching providers is config + an adapter.

---

## 12. Circuit Breaker — for downstream services

For Maps and Payment:

```java
public class CircuitBreakerMapsService implements MapsService {
  private final MapsService delegate;
  private final CircuitBreaker breaker;
  public Distance distance(Location a, Location b) {
    return breaker.execute(() -> delegate.distance(a,b),
                           fallback -> Distance.estimateFromHaversine(a,b));
  }
}
```

If Maps is down, we degrade to haversine distance — less accurate but the system stays up.

---

## SOLID compliance

- **S**: pricing rules, validators, scoring strategies — each class one job.
- **O**: adding a city, ride type, or pricing rule = adding a class.
- **L**: every Strategy honors its interface; no thrown `UnsupportedOperationException`.
- **I**: small interfaces (`MapsService` has 3 methods, not 30).
- **D**: services depend on `MapsService`, `PaymentGateway`, `DriverFinder` — never concrete classes.

---

## What we deliberately avoided

- **Singleton pattern** — implicit globals are testing nightmares.
- **Inheritance hierarchies for Ride types** (StandardRide, XLRide, PoolRide as classes) — overkill; an enum + per-type configuration object is cleaner.
- **Service Locator** — anti-pattern; obscures dependencies. We use constructor DI.

These choices were deliberate. State them in interviews.
