# 05 · Design Patterns for LLD — 9 Patterns You'll Use 90% of the Time

> Memorize these. They cover almost every staff-level LLD problem.

For each pattern we cover:
1. **What problem it solves**
2. **When to use / when not**
3. **Implementation in Java**
4. **Tradeoffs and alternatives**
5. **Where it appears in this repo**

---

## 1. Strategy Pattern

> **Encapsulate interchangeable algorithms behind a common interface.**

### Problem

You have **multiple algorithms** that take the **same input** and produce the **same output**, but the choice depends on context.

Examples:
- Pricing: standard / surge / promo / loyalty
- Splitting: equal / exact / percentage / share
- Dispatch: nearest / least-busy / highest-rated
- Sorting: relevance / distance / rating

### Bad (without Strategy)

```java
public Money price(Ride r, String mode) {
  if ("STANDARD".equals(mode)) return base(r);
  if ("SURGE".equals(mode))    return base(r).multiply(surgeFactor(r));
  if ("FLAT".equals(mode))     return Money.of(199);
  throw new IllegalArgumentException();
}
```

This violates OCP. Every new mode = edit here.

### Good

```java
public interface PricingStrategy {
  Money price(Ride ride);
}

public class StandardPricing implements PricingStrategy {
  public Money price(Ride r) { return baseFare(r).add(distanceFare(r)).add(timeFare(r)); }
}

public class SurgePricing implements PricingStrategy {
  private final SurgeFactorProvider provider;
  public SurgePricing(SurgeFactorProvider p) { this.provider = p; }
  public Money price(Ride r) {
    Money base = new StandardPricing().price(r);
    return base.multiply(provider.factorAt(r.pickup(), r.requestedAt()));
  }
}

public class PricingService {
  private final Map<RideType, PricingStrategy> strategies;
  public PricingService(Map<RideType, PricingStrategy> s) { this.strategies = s; }
  public Money price(Ride r) {
    return strategies.get(r.type()).price(r);
  }
}
```

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| OCP-friendly | Indirection (more classes) |
| Testable in isolation | Strategy must be stateless or thread-safe |
| Swap at runtime | Composing many strategies needs care |

### Alternatives

- Lambdas / functional interfaces if the strategy has one method.
- `Map<Type, Function<Input, Output>>` for very small strategies.

### In this repo

- `01_Food_Delivery_System` — `DispatchStrategy`, `PricingStrategy`
- `02_Ride_Booking_System` — `MatchingStrategy`, `SurgeStrategy`
- `05_Splitwise` — `SplitStrategy` (equal / exact / percent / share)

---

## 2. State Pattern

> **Object's behavior changes when its internal state changes** — make state explicit and behavior polymorphic.

### Problem

You have an entity with a lifecycle (Order, Ride, Booking) and behavior depends on current state. Plain `if/switch(status)` becomes a 200-line method.

### Bad

```java
public void cancel(Order o) {
  switch (o.status()) {
    case PLACED:    refundFull(o); o.setStatus(CANCELLED); return;
    case CONFIRMED: refundFull(o); notifyRestaurantToCancel(o); o.setStatus(CANCELLED); return;
    case PREPARING: throw new IllegalStateException("too late");
    case DELIVERED: throw new IllegalStateException("already delivered");
    // ...12 more cases per action
  }
}
```

### Good

```java
public interface OrderState {
  void cancel(OrderContext ctx);
  void confirm(OrderContext ctx);
  void markPreparing(OrderContext ctx);
  // etc
  OrderStatus status();
}

public class PlacedState implements OrderState {
  public void cancel(OrderContext c) {
    c.refundFull();
    c.transitionTo(new CancelledState());
  }
  public void confirm(OrderContext c) {
    c.transitionTo(new ConfirmedState());
  }
  public void markPreparing(OrderContext c) {
    throw new IllegalStateException("must confirm first");
  }
  public OrderStatus status() { return OrderStatus.PLACED; }
}

public class OrderContext {
  private OrderState state;
  public void cancel()  { state.cancel(this);  }
  public void confirm() { state.confirm(this); }
  void transitionTo(OrderState next) { this.state = next; }
}
```

Each state encapsulates legal transitions and behavior.

### When NOT to use

- 2–3 states with simple behavior → just use an `enum`.
- Behavior is identical across states → unnecessary.

### In this repo

- `01_Food_Delivery_System/09_state_machines.md` — Order lifecycle.
- `02_Ride_Booking_System` — Ride states.
- `03_Hotel_Booking_System` — Booking lifecycle.

---

## 3. Factory Pattern (Method + Abstract)

> **Hide the construction details of an object behind a stable interface.**

### Factory Method (one-axis)

```java
public interface NotificationFactory {
  Notifier create(NotificationChannel channel);
}

public class DefaultNotificationFactory implements NotificationFactory {
  public Notifier create(NotificationChannel ch) {
    return switch (ch) {
      case SMS   -> new SmsNotifier(twilio);
      case EMAIL -> new EmailNotifier(sendgrid);
      case PUSH  -> new PushNotifier(fcm);
    };
  }
}
```

### Abstract Factory (two-axis: family of related objects)

You need a Light theme **family** — Button, Slider, Modal, Tooltip — and a Dark theme family. Or a Postgres family — Connection, Statement, ResultSet — vs a MySQL family.

```java
public interface UiFactory {
  Button createButton();
  Slider createSlider();
  Modal createModal();
}

public class LightThemeFactory implements UiFactory { ... }
public class DarkThemeFactory implements UiFactory { ... }
```

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| Hides construction | Adding a method to the family hits every factory |
| Centralizes wiring | Can hide too much (test-unfriendly) |
| Variants swappable | Beware of going overboard for trivial cases |

### When NOT

If you're constructing one type with one constructor — just use `new`. Factory exists when there's **a choice**.

---

## 4. Observer / Event Pub-Sub

> **One-to-many notification of state change without coupling subjects to observers.**

### Problem

When an order is placed, multiple side effects must run:
- Notify restaurant
- Reserve a driver
- Send customer SMS
- Update analytics
- Trigger fraud check

Putting all 5 inside `OrderService.placeOrder()` couples the service to all of them. Worse, adding a 6th means editing the service (OCP violation).

### Good

```java
public interface DomainEvent {}
public record OrderPlaced(UUID orderId, UUID customerId, Instant at) implements DomainEvent {}

public interface EventPublisher { void publish(DomainEvent e); }

public interface EventHandler<E extends DomainEvent> {
  Class<E> eventType();
  void handle(E event);
}

public class InMemoryEventBus implements EventPublisher {
  private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();

  public <E extends DomainEvent> void register(EventHandler<E> h) {
    handlers.computeIfAbsent(h.eventType(), k -> new CopyOnWriteArrayList<>()).add(h);
  }

  @SuppressWarnings("unchecked")
  public void publish(DomainEvent e) {
    List<EventHandler<?>> list = handlers.getOrDefault(e.getClass(), List.of());
    for (EventHandler h : list) h.handle(e);
  }
}
```

Sample handlers:

```java
public class NotifyRestaurantHandler implements EventHandler<OrderPlaced> { ... }
public class ReserveDriverHandler   implements EventHandler<OrderPlaced> { ... }
```

### Sync vs Async

In-process Observer is **synchronous**. For real systems you want async (Kafka / RabbitMQ / SQS) so a slow consumer doesn't block the order placement.

```
sync   pub-sub (Observer):   easy, fast, but failures bubble
async  pub-sub (broker):     decoupled, durable, eventually consistent
```

### Where it appears

- Every system uses event publishing for cross-module side effects.
- Splitwise — `ExpenseAdded` triggers balance recompute.
- Hotel — `BookingConfirmed` triggers notification + accounting.

---

## 5. Command Pattern

> **Encapsulate a request as an object** so it can be queued, logged, undone, retried.

### Problem

Some operations need to be:
- **Queued** (process later)
- **Audited** (who did what)
- **Replayable** (re-run from a log)
- **Undoable** (saga compensation)
- **Idempotent** (run again safely)

A regular method call has none of these properties.

### Implementation

```java
public interface Command<R> {
  R execute(CommandContext ctx);
  default void undo(CommandContext ctx) {}
}

public record PlaceOrderCommand(UUID customerId, UUID restaurantId,
                                List<LineItem> items, String idempotencyKey)
    implements Command<Order> {

  @Override public Order execute(CommandContext ctx) {
    return ctx.orderService().placeOrder(this);
  }
}

public record CancelOrderCommand(UUID orderId, String reason)
    implements Command<Void> {
  @Override public Void execute(CommandContext ctx) {
    ctx.orderService().cancel(orderId, reason);
    return null;
  }
}
```

### Where it shines

- **Saga orchestration** — each step is a command with an undo.
- **Audit log** — serialize commands as JSON; replay later.
- **Async queues** — workers pull and `execute()` commands.
- **CLIs / REPL** — natural fit.

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| Auditable, queueable, undoable | Boilerplate for simple cases |
| Separates request from execution | Object explosion if overused |

---

## 6. Decorator Pattern

> **Wrap an object to add behavior without subclassing.**

### Problem

You want to add logging, caching, retries, metrics, encryption — to an existing service — without polluting its core logic.

### Implementation

```java
public interface OrderService { Order placeOrder(PlaceOrderCommand c); }

public class CoreOrderService implements OrderService { ... }

public class LoggingOrderService implements OrderService {
  private final OrderService delegate;
  public LoggingOrderService(OrderService d) { this.delegate = d; }
  public Order placeOrder(PlaceOrderCommand c) {
    log.info("placeOrder {}", c);
    Order r = delegate.placeOrder(c);
    log.info("placed -> {}", r.id());
    return r;
  }
}

public class CachingOrderService implements OrderService {
  private final OrderService delegate;
  private final Cache<String, Order> cache;
  public Order placeOrder(PlaceOrderCommand c) {
    return cache.get(c.idempotencyKey(), () -> delegate.placeOrder(c));
  }
}

public class RetryingOrderService implements OrderService {
  private final OrderService delegate;
  public Order placeOrder(PlaceOrderCommand c) {
    int tries = 0;
    while (true) {
      try { return delegate.placeOrder(c); }
      catch (TransientException e) { if (++tries == 3) throw e; sleep(backoff(tries)); }
    }
  }
}

OrderService svc = new LoggingOrderService(
                     new CachingOrderService(
                       new RetryingOrderService(
                         new CoreOrderService(...))));
```

This is exactly how middleware in HTTP frameworks works.

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| Add behavior without changing core | Order of decorators matters |
| Combine freely | Stack traces become longer |
| Each concern is independent | Many tiny classes |

---

## 7. Repository Pattern

> **Abstract persistence behind a domain-shaped interface.**

### Why not just call ORM directly?

ORM-leakage happens. Then your domain knows about JDBC, JPA EntityManagers, query strings, and your service tests need a database.

### Implementation

```java
public interface OrderRepository {                       // domain-owned
  Optional<Order> findById(UUID id);
  Order save(Order order);                               // upsert
  Optional<Order> findByIdempotencyKey(String key);
  List<Order> findActiveByCustomer(UUID customerId);
}

public class JpaOrderRepository implements OrderRepository { ... }   // infra-owned
public class InMemoryOrderRepository implements OrderRepository { ... }   // for tests
```

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| Swap DBs | Easy to add too many query methods |
| Test with in-memory | Risk of "Repository" doing too much (becomes a query DSL) |
| Domain stays pure | Overkill for tiny apps |

### Anti-pattern: Generic Repository

```java
interface Repository<T, ID> {
  T findById(ID);
  T save(T);
  void delete(T);
}
```

Looks DRY, but every domain has different read patterns. Prefer **domain-specific** repositories.

---

## 8. Builder Pattern

> **Construct complex objects step-by-step.**

### Problem

```java
new Order(customerId, restaurantId, items, deliveryAddr, paymentMethod,
          promoCode, scheduledAt, instructions, contactlessFlag, tipAmount, ...);
```

12 positional arguments. Misorder one and you're delivering to the wrong address.

### Builder

```java
public final class Order {
  private final UUID customerId;
  // ...
  private Order(Builder b) { ... }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private UUID customerId;
    // ...
    public Builder customerId(UUID id) { this.customerId = id; return this; }
    public Builder addItem(LineItem item) { this.items.add(item); return this; }
    public Order build() {
      requireNonNull(customerId, "customerId");
      // invariants
      return new Order(this);
    }
  }
}

Order o = Order.builder()
  .customerId(cId).restaurantId(rId)
  .addItem(item1).addItem(item2)
  .deliveryAddress(addr).paymentMethod(pm)
  .build();
```

### Variants

- **Fluent**: as above.
- **Telescoping**: required fields in static factory, optional via setters.
- **Immutable + Builder**: combine with records — store fields final, validate in `build()`.

### When NOT

3 or fewer fields → use a constructor or record.

---

## 9. Chain of Responsibility

> **Pass a request along a chain of handlers until one handles it.**

### Problem

Validation, authorization, fraud checks, rate limits — each runs in sequence, each may short-circuit.

### Implementation

```java
public interface Handler<C> {
  void handle(C ctx, HandlerChain<C> chain);
}

public class HandlerChain<C> {
  private final List<Handler<C>> handlers;
  private int idx = 0;
  public HandlerChain(List<Handler<C>> hs) { this.handlers = hs; }
  public void next(C ctx) {
    if (idx == handlers.size()) return;
    handlers.get(idx++).handle(ctx, this);
  }
}

public class AuthHandler<OrderRequest> implements Handler<OrderRequest> {
  public void handle(OrderRequest r, HandlerChain<OrderRequest> chain) {
    if (!auth.isValid(r.token())) throw new UnauthorizedException();
    chain.next(r);
  }
}

public class FraudHandler<OrderRequest> implements Handler<OrderRequest> {
  public void handle(OrderRequest r, HandlerChain<OrderRequest> chain) {
    if (fraud.score(r) > 0.9) throw new FraudDetectedException();
    chain.next(r);
  }
}

new HandlerChain<>(List.of(
  new AuthHandler(),
  new RateLimitHandler(),
  new FraudHandler(),
  new InventoryHandler()
)).next(request);
```

### Tradeoffs

| ✔ | ✘ |
| --- | --- |
| Pluggable, ordered | Long chains can be hard to debug |
| Each handler has SRP | Implicit ordering can hide bugs |
| Add/remove cleanly | Sometimes simpler with linear code |

### When to use

- HTTP middleware
- ETL pipelines
- Approval workflows (manager → director → VP)
- Fraud rules

---

## Pattern Selection Cheat Sheet

```
Multiple algorithms, same I/O                  → Strategy
Lifecycle / state-dependent behavior           → State
Hide construction with choices                 → Factory / Abstract Factory
One-to-many notification                       → Observer / Pub-Sub
Encapsulate request                            → Command
Add cross-cutting behavior                     → Decorator
Persistence abstraction                        → Repository
Complex object construction                    → Builder
Pluggable sequential processing                → Chain of Responsibility
```

When asked "what pattern?" in an interview, **pick exactly one** and justify with the trigger above. Saying "I could use Strategy or Factory" sounds confused; "I'm using Strategy because the algorithm varies but the contract is stable" sounds confident.

---

## Anti-Pattern: Pattern Soup

If you find yourself using **5+ patterns** in a single small class, you're over-engineering. Patterns should make a design *clearer*, not denser. A 100-line correct service > a 1000-line "pattern showcase."

---

## Checklist

- [ ] Every place I used a pattern, I can name the **trigger** that justified it.
- [ ] No pattern is used "because it looks senior."
- [ ] Strategy → for variation. State → for lifecycle. Factory → for choice. Repository → for persistence. Decorator → for cross-cutting. Observer → for fan-out. Builder → for complex construction. Command → for queueable/undoable. Chain → for ordered filters.
