# 04 · SOLID Principles — Deep Dive with LLD Examples

> Most "bad code" you see in interviews is just an unrecognized SOLID violation. Once you can name the violation, you can name the fix.

---

## SOLID, in one line each

```
S - Single Responsibility    | One reason to change.
O - Open/Closed              | Open for extension, closed for modification.
L - Liskov Substitution      | Subtypes must be usable wherever base is.
I - Interface Segregation    | Many small interfaces > one fat one.
D - Dependency Inversion     | Depend on abstractions, not concretions.
```

These aren't five rules; they're **five lenses** to inspect your design.

---

## S — Single Responsibility Principle

> A class should have **one reason to change**.

"One reason" means **one stakeholder / one axis of change**. Not "one method."

### Bad

```java
public class Order {
  private List<OrderItem> items;
  private OrderStatus status;

  public void addItem(OrderItem item) { ... }

  public BigDecimal calculateTotal(TaxStrategy tax) { ... }   // pricing axis

  public void chargeCustomer(PaymentGateway gw) { ... }       // payments axis

  public void notifyRestaurant(SMSClient sms) { ... }         // notifications axis

  public void persist(Connection conn) { ... }                // persistence axis

  public String toEmailHtml() { ... }                         // presentation axis
}
```

`Order` has **five reasons to change**: pricing rules, payment provider switch, SMS vendor swap, DB migration, email template change.

### Good

```java
public class Order {
  private final List<OrderItem> items;
  private OrderStatus status;
  public BigDecimal total() { ... }   // pure domain logic; no IO
  public void cancel() { ... }
}

public class PriceCalculator { public Money compute(Order o, TaxRules r) { ... } }
public class PaymentProcessor { public Payment charge(Order o) { ... } }
public class OrderNotifier { public void onPlaced(Order o) { ... } }
public class OrderRepository { public void save(Order o) { ... } }
public class OrderEmailRenderer { public String render(Order o) { ... } }
```

Each class has one stakeholder. SRP is **the** prerequisite for the other 4.

### When NOT to over-split

A `User` with `firstName`, `lastName`, `email`, `phone`, and getters/setters is **fine** as one class. SRP is about **reasons to change**, not method count.

---

## O — Open/Closed Principle

> Open for extension, closed for modification.

You should be able to **add a feature** by **adding a class**, not by **editing existing classes**.

### Bad

```java
public class PriceCalculator {
  public Money compute(Order o, String discountType) {
    Money total = Money.of(o.subtotal());
    if ("FLAT_50".equals(discountType)) {
      total = total.subtract(Money.of(50));
    } else if ("PERCENT_10".equals(discountType)) {
      total = total.multiply(0.9);
    } else if ("BOGO".equals(discountType)) {
      // ...30 lines...
    } // ← every new promo means editing this method
    return total;
  }
}
```

### Good — Strategy + Open/Closed

```java
public interface DiscountStrategy {
  Money apply(Money subtotal, Order order);
}

public class FlatDiscount implements DiscountStrategy {
  private final Money amount;
  public FlatDiscount(Money amount) { this.amount = amount; }
  public Money apply(Money subtotal, Order o) { return subtotal.subtract(amount); }
}

public class PercentDiscount implements DiscountStrategy {
  private final BigDecimal pct;
  public PercentDiscount(BigDecimal pct) { this.pct = pct; }
  public Money apply(Money subtotal, Order o) { return subtotal.multiply(BigDecimal.ONE.subtract(pct)); }
}

public class PriceCalculator {
  public Money compute(Order o, List<DiscountStrategy> discounts) {
    Money total = o.subtotal();
    for (DiscountStrategy d : discounts) total = d.apply(total, o);
    return total;
  }
}
```

Adding a new discount = **new class**, no edits to `PriceCalculator`.

### Trap: "Open for extension" doesn't mean "always polymorphic"

A `User` with `getFullName() = firstName + " " + lastName` does not need to be subclassable. OCP applies when you have **a known axis of variation** (discount type, payment method, dispatch algorithm).

---

## L — Liskov Substitution Principle

> A subtype must be substitutable for its base type **without changing program correctness.**

Most LSP violations come from sloppy inheritance hierarchies.

### Classic violation: rectangle/square

```java
class Rectangle {
  void setWidth(int w) { width = w; }
  void setHeight(int h) { height = h; }
}

class Square extends Rectangle {
  void setWidth(int w)  { width = w; height = w; }   // ← changes base behavior
  void setHeight(int h) { width = h; height = h; }
}

// caller assumes:
Rectangle r = ...;
r.setWidth(5);
r.setHeight(10);
assert r.area() == 50;   // FAILS for Square (area = 100)
```

Fix: don't make `Square extends Rectangle`. They are not in an "is-a" relationship under mutation.

### LSP in LLD interviews

Bad inheritance hides LSP issues. Examples:

- `PrepaidPayment extends Payment` but throws on `refund()` ← violates LSP.
- `ReadOnlyList extends List` but throws on `add()` ← Java's `Collections.unmodifiableList` is the canonical wrong example.

**Rule:** if your subclass throws `UnsupportedOperationException` for a method, you've violated LSP. Use composition or smaller interfaces (ISP) instead.

### Good

```java
public interface Refundable { void refund(); }
public interface Payable    { void charge(); }

public class CardPayment implements Payable, Refundable { ... }
public class PrepaidPayment implements Payable { ... }   // can't refund, doesn't implement
```

LSP and ISP fix each other.

---

## I — Interface Segregation Principle

> Clients should not depend on methods they don't use.

Big "fat" interfaces force every implementer to deal with methods they don't care about. Worse, they bind unrelated callers to changes.

### Bad

```java
public interface OrderRepository {
  Order save(Order o);
  Optional<Order> findById(UUID id);
  List<Order> findByCustomer(UUID id);
  List<Order> findByRestaurant(UUID id);
  List<Order> search(String fullText);          // for support agent
  List<Order> findStuck(Duration olderThan);    // for ops monitoring
  Map<String, BigDecimal> revenueByDay();       // for analytics
  void purgeOlderThan(Duration d);              // for compliance
}
```

A test for the customer-facing service now has to mock 8 methods. The repository has 8 reasons to change.

### Good

```java
public interface OrderRepository {
  Order save(Order o);
  Optional<Order> findById(UUID id);
}

public interface OrderQueryRepository {           // for read-side flows
  List<Order> findByCustomer(UUID id);
  List<Order> findByRestaurant(UUID id);
}

public interface OrderSearchRepository { List<Order> search(String q); }
public interface OrderMonitoringRepository { List<Order> findStuck(Duration d); }
public interface OrderAnalyticsRepository { Map<String,BigDecimal> revenueByDay(); }
```

A single class can `implements` many — that's fine. Clients **depend on** only what they need.

### How small is too small?

ISP is not "every method gets its own interface." It is "interfaces match **client roles**." If two methods are always used together by the same callers, keep them together.

---

## D — Dependency Inversion Principle

> Depend on abstractions, not on concretions. **High-level modules should not depend on low-level modules; both should depend on abstractions.**

### Bad

```java
public class OrderService {
  private final PostgresOrderRepository repo = new PostgresOrderRepository();
  private final SendgridEmailClient email = new SendgridEmailClient();
  private final StripeGateway stripe = new StripeGateway();
  // ...
}
```

`OrderService` is glued to Postgres, Sendgrid, Stripe. You can't:
- Swap to MySQL without rewriting OrderService.
- Unit test without standing up real services.
- Mock for chaos testing.

### Good — depend on abstractions

```java
public class OrderService {
  private final OrderRepository repo;
  private final EmailClient email;
  private final PaymentGateway payments;

  public OrderService(OrderRepository r, EmailClient e, PaymentGateway p) {
    this.repo = r; this.email = e; this.payments = p;
  }
}
```

Now:
- `OrderService` depends on **abstractions** (`OrderRepository`, `EmailClient`, `PaymentGateway`).
- `PostgresOrderRepository` and `OrderService` both depend on `OrderRepository`. Dependency is **inverted** — both point inward toward the abstraction.

```
┌──────────────┐         ┌──────────────────┐
│ OrderService │ ──────▶ │ OrderRepository  │ ◀────── PostgresOrderRepository
└──────────────┘         └──────────────────┘
   high-level                abstraction              low-level
```

### Wiring (Dependency Injection)

DI is the **mechanism**; DIP is the **principle**.

```java
// Composition root (the only place that knows concrete types)
DataSource ds = ...;
OrderRepository repo = new PostgresOrderRepository(ds);
PaymentGateway pg = new StripeGateway(stripeKey);
EmailClient mail = new SendgridEmailClient(sgKey);
OrderService orders = new OrderService(repo, mail, pg);
```

Or with a framework (Spring, Guice). The principle is the same.

---

## Putting SOLID Together — A Worked Example

Let's design a `RideMatchingService`.

### Bad (violates all 5)

```java
public class RideMatchingService {

  public void matchRide(Ride ride) {
    // SRP: this one method does selection, scoring, persistence, notification, surge calc
    List<Driver> drivers = new MySqlDriverDao().findNearby(ride.getPickup(), 3);
    Driver best = null;
    double bestScore = -1;
    for (Driver d : drivers) {
      double score;
      if (ride.getType() == "POOL") {
        // pool scoring: 10 lines
      } else if (ride.getType() == "PRIORITY") {
        // priority scoring: 8 lines  ← OCP violation: new types = edit here
      } else {
        // standard
      }
      if (score > bestScore) best = d;
    }
    new TwilioSmsClient().send(best.getPhone(), "you got a ride");   // DIP violation
    new MySqlDriverDao().setBusy(best.getId());
  }
}
```

### Good

```java
// SRP: each class has one job
public interface DriverFinder { List<Driver> nearby(Location l, double km); }
public interface ScoringStrategy { double score(Driver d, Ride r); }   // OCP, Strategy
public interface DriverNotifier { void notify(Driver d, Ride r); }
public interface DriverRepository { void markBusy(UUID id); }

public class RideMatchingService {
  private final DriverFinder finder;
  private final Map<RideType, ScoringStrategy> scorers;   // OCP
  private final DriverNotifier notifier;
  private final DriverRepository drivers;

  public RideMatchingService(DriverFinder f,
                             Map<RideType, ScoringStrategy> scorers,
                             DriverNotifier n,
                             DriverRepository d) {  // DIP: all abstractions
    this.finder = f; this.scorers = scorers; this.notifier = n; this.drivers = d;
  }

  public Optional<Driver> match(Ride ride) {
    ScoringStrategy scorer = scorers.get(ride.type());
    return finder.nearby(ride.pickup(), 3.0).stream()
        .max(Comparator.comparingDouble(d -> scorer.score(d, ride)))
        .map(d -> { drivers.markBusy(d.id()); notifier.notify(d, ride); return d; });
  }
}
```

### How each principle shows up

- **S**: matching logic separated from finding, notifying, persisting.
- **O**: new ride type = new `ScoringStrategy` impl, no edits to service.
- **L**: every `ScoringStrategy` returns a `double`; nothing throws.
- **I**: `DriverFinder` is small, doesn't include `markBusy()`.
- **D**: service depends on interfaces; concretes wired in composition root.

Always be ready to point at each principle in your design.

---

## When SOLID Hurts

SOLID is a tool, not a religion. Apply it when:

- The system has known axes of variation (use OCP/Strategy).
- Multiple stakeholders push changes (use SRP).
- You need testability (use DIP).

Don't apply it when:

- The class is a simple data holder.
- The "extension" point will never be reached.
- You'd be adding 3 layers of indirection for one fixed implementation.

> "**Premature abstraction** is as bad as **premature optimization**."

---

## Common Anti-Patterns Mapped to Violations

| Anti-pattern | Violates |
| --- | --- |
| God class (10 responsibilities) | S |
| Switch-on-type | O (and L if behavior differs) |
| Empty/throw method override | L |
| 30-method "service" interface | I |
| `new X()` inside business logic | D |
| Static singletons everywhere | D (hard to test) |
| Class that knows about `Connection` and HTTP request | S, D |

If you spot one of these in an interview, **name the violation** before fixing it. That's the staff-level move.

---

## Checklist

- [ ] Each domain class has one reason to change.
- [ ] Each variation point uses Strategy / polymorphism, not switch.
- [ ] No subclass throws `UnsupportedOperationException`.
- [ ] No interface has methods used by < 50% of callers.
- [ ] Services depend on interfaces, not concrete classes.
- [ ] Composition root is the only place that calls `new` on infrastructure.
