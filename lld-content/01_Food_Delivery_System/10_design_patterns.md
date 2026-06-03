# 10 · Food Delivery — Design Patterns Used

For each pattern: **why we used it**, **alternatives**, **implementation**.

---

## 1. Strategy — pricing rules

### Why

Pricing has many rules: tax, delivery fee, surge, promo, loyalty, subscription discount. The set evolves rapidly. We need to **add a rule without editing existing rules** (OCP).

### Alternative

A monolithic `PriceCalculator.compute()` with `if/else`. Becomes a 300-line nightmare; every promo means editing the file.

### Implementation

```java
public interface PricingRule {
  void apply(Cart cart, PriceBreakdownBuilder b);
}

public class TaxRule implements PricingRule {
  private final BigDecimal pct;
  public TaxRule(BigDecimal p) { this.pct = p; }
  public void apply(Cart cart, PriceBreakdownBuilder b) {
    b.tax(b.subtotal().multiply(pct));
  }
}

public class SurgeRule implements PricingRule {
  private final SurgeFactorProvider provider;
  public void apply(Cart cart, PriceBreakdownBuilder b) {
    BigDecimal f = provider.factorAt(cart.pickupLocation(), Instant.now());
    if (f.compareTo(BigDecimal.ONE) > 0) {
      b.surge(b.subtotal().multiply(f.subtract(BigDecimal.ONE)));
    }
  }
}

public class PromoCodeRule implements PricingRule {
  private final PromoEngine engine;
  public void apply(Cart cart, PriceBreakdownBuilder b) {
    Money d = engine.evaluate(cart);
    if (d.isPositive()) b.discount(d);
  }
}

public class PricingService {
  private final List<PricingRule> rules;
  public PricingService(List<PricingRule> r) { this.rules = r; }
  public PriceBreakdown compute(Cart c) {
    PriceBreakdownBuilder b = new PriceBreakdownBuilder(c);
    rules.forEach(r -> r.apply(c, b));
    return b.build();
  }
}
```

### Tradeoffs

- ✔ Each rule is unit-testable.
- ✔ Adding a rule = adding a class.
- ✘ Order of rules matters; document it.

---

## 2. Strategy — dispatch scoring

### Why

We need to vary how we **score drivers** for an order:

- Default: nearest first.
- Weighted: distance, busy-rate, rating, fairness.
- Batch-aware: prefer drivers who can pick up multiple nearby orders.

The choice is per-city or per-experiment.

### Implementation

```java
public interface ScoringStrategy {
  double score(Driver d, Order o, Location pickup);
}

public class NearestFirstScoring implements ScoringStrategy {
  public double score(Driver d, Order o, Location pickup) {
    return -d.lastLocation().distanceKm(pickup);
  }
}

public class WeightedScoring implements ScoringStrategy {
  public double score(Driver d, Order o, Location pickup) {
    double dist = d.lastLocation().distanceKm(pickup);
    double rating = d.rating();
    double idleTime = d.minutesIdle();
    return -dist * 1.0 + rating * 0.5 + idleTime * 0.1;     // simplified
  }
}

public class BatchAwareScoring implements ScoringStrategy {
  // boost drivers near another order's restaurant
}
```

`DispatchService` is constructor-injected with the chosen strategy. Switching is a config change.

---

## 3. State pattern (or enum-driven) — order lifecycle

Covered in `09_state_machines.md`. We use a **transition map** for compactness; we mention `State` pattern as the alternative when behavior diverges.

---

## 4. Repository pattern — persistence

### Why

The domain layer must not know about JPA, JDBC, or query strings. Repositories live in the domain package as **interfaces**, with implementations in `infra`.

### Implementation

```java
public interface OrderRepository {
  Optional<Order> findById(UUID id);
  Order save(Order order);
  Optional<Order> findByIdempotencyKey(String key);
}

public class JpaOrderRepository implements OrderRepository {
  private final EntityManager em;
  // ...
}

public class InMemoryOrderRepository implements OrderRepository {  // for tests
  private final ConcurrentMap<UUID, Order> store = new ConcurrentHashMap<>();
}
```

Tests use the in-memory; production wires JPA.

---

## 5. Observer / Pub-sub — domain events

### Why

When an order is placed, ~5 things must happen (notify, dispatch, analytics, fraud, email). We do not couple `OrderService` to all of them.

### Implementation

In-process publisher:

```java
public interface EventPublisher { void publish(DomainEvent e); }

public class OutboxEventPublisher implements EventPublisher {
  private final OutboxRepository outbox;
  public void publish(DomainEvent e) {
    outbox.append(toRecord(e));    // same transaction as domain write
  }
}
```

Background poller pushes outbox rows to Kafka. Each consumer is independent.

We discussed sync vs async pub-sub in `00_End_To_End_LLD_Tutorial/05_design_patterns.md`. Outbox + Kafka gives us **eventual consistency with durability**.

---

## 6. Command pattern — placeOrder, cancelOrder

### Why

Place / cancel / reject / pickup / deliver are **commands**. Treating them as objects:

- Lets us **log** and **replay** them in audit.
- Lets us **enqueue** them for async processing.
- Aligns with CQRS-lite designs.

### Implementation

```java
public record PlaceOrderCommand(
  UUID customerId,
  UUID restaurantId,
  List<LineItem> items,
  UUID deliveryAddressId,
  UUID paymentMethodId,
  String idempotencyKey
) {}

public record CancelOrderCommand(UUID orderId, CancelReason reason) {}
```

Commands are POJOs/records. They flow into `OrderService.placeOrder(cmd)` / `cancel(cmd)`.

---

## 7. Decorator — cross-cutting concerns

### Why

Want to add **logging**, **metrics**, **caching**, **retries** without polluting `OrderService`.

### Implementation

```java
public class LoggingOrderService implements OrderService {
  private final OrderService inner;
  public Order placeOrder(PlaceOrderCommand c) {
    log.info("placeOrder cust={} rest={}", c.customerId(), c.restaurantId());
    Order o = inner.placeOrder(c);
    log.info("placed orderId={} total={}", o.id(), o.priceBreakdown().total());
    return o;
  }
  // ...
}

public class MetricsOrderService implements OrderService { ... }
public class RetryingOrderService implements OrderService { ... }

OrderService svc = new LoggingOrderService(
                     new MetricsOrderService(
                       new RetryingOrderService(
                         new CoreOrderService(...))));
```

In a Spring app you'd use AOP; the principle is identical.

---

## 8. Builder — Order construction

### Why

`Order` has many fields, several optional (promo, instructions). Positional constructors are error-prone.

### Implementation

```java
Order o = Order.builder()
  .customer(customerId)
  .restaurant(restaurantId)
  .addItem(item1)
  .addItem(item2)
  .deliverySnapshot(snap)
  .priceBreakdown(price)
  .idempotencyKey(key)
  .build();
```

`build()` validates invariants (item count ≥ 1, total ≥ 0) before returning.

---

## 9. Chain of Responsibility — order placement validations

### Why

Many checks must run in order, each can short-circuit:
- Auth check
- Rate limit
- Customer block-list
- Restaurant active & in hours
- Items belong to restaurant & available
- Inventory in stock
- Delivery zone within restaurant radius
- Payment method valid

### Implementation

```java
public interface PlaceOrderValidator {
  void validate(PlaceOrderCommand cmd, ValidationContext ctx);
}

public class RestaurantActiveValidator implements PlaceOrderValidator {
  public void validate(PlaceOrderCommand cmd, ValidationContext ctx) {
    Restaurant r = ctx.restaurants().require(cmd.restaurantId());
    if (!r.isActive())  throw new RestaurantClosedException();
    if (!r.isOpenAt(Instant.now())) throw new RestaurantClosedException();
  }
}

public class ItemsAvailableValidator implements PlaceOrderValidator { ... }

public class PlaceOrderValidationChain {
  private final List<PlaceOrderValidator> chain;
  public void validate(PlaceOrderCommand cmd, ValidationContext ctx) {
    for (var v : chain) v.validate(cmd, ctx);
  }
}
```

Adding a new check = adding a validator.

---

## 10. Factory — geospatial finder

### Why

We have multiple `DriverFinder` implementations: Redis Geo for production, JpaDriverFinder for tests / fallback, an Elasticsearch one for V2.

```java
public interface DriverFinder {
  List<Driver> findCandidates(Location pickup, double radiusKm, int limit);
}

public class DriverFinderFactory {
  public static DriverFinder of(FinderType t, Dependencies d) {
    return switch (t) {
      case REDIS_GEO -> new RedisGeoDriverFinder(d.redis());
      case JDBC      -> new JpaDriverFinder(d.jdbc());
      case STUB      -> new StubDriverFinder(...);
    };
  }
}
```

Composition root selects the implementation; service depends on the interface.

---

## Pattern map summary

| Pattern | Where |
| --- | --- |
| Strategy | Pricing rules, dispatch scoring |
| State | Order, Driver lifecycles |
| Repository | All persistence |
| Observer / Pub-sub (Outbox) | Side effects on order placed/cancelled/delivered |
| Command | placeOrder, cancelOrder, etc. |
| Decorator | Logging, metrics, retry on services |
| Builder | Order construction |
| Chain of Responsibility | Order placement validation pipeline |
| Factory | DriverFinder selection |

> Notice that **none** of these patterns are used "to look senior." Each has a clear trigger we identified during requirement analysis.

---

## SOLID compliance summary

- **S**: Order, Driver, Pricing rules, Validators each have one reason to change.
- **O**: Pricing, Dispatch, Validators all OCP-friendly via Strategy / Chain.
- **L**: All Strategy implementations honor the interface contract; no thrown `UnsupportedOperationException`.
- **I**: Repository and Service interfaces are minimal; no fat 20-method service.
- **D**: All services depend on interfaces (`OrderRepository`, `EventPublisher`, `DriverFinder`); concretes wired in composition root.

We maintain SOLID by construction.
