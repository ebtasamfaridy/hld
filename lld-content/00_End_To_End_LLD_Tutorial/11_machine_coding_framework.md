# 11 · Machine Coding Framework

> The machine coding round (60–120 min) tests your ability to **build a clean, working system from scratch.** Most candidates fail not because they can't code, but because they have no template.

This file is your template.

---

## What's Tested in Machine Coding

| Dimension | Weight |
| --- | --- |
| Correct happy path | 25% |
| Code quality / cleanliness | 25% |
| Extensibility (OCP) | 15% |
| Concurrency / correctness | 15% |
| Test coverage | 10% |
| Edge cases | 10% |

If you ship a working happy path with an extensible structure and one or two test classes, you'll score above the bar.

---

## Pre-Round Setup (do this before the interview)

A practiced candidate has these **memorized and ready**:

```
1. Project skeleton (Maven/Gradle, package layout)
2. README template
3. Service-Repo-Domain layering snippet
4. Strategy + Factory pattern snippet
5. Idempotency / version column snippet
6. JUnit 5 / Mockito boilerplate
7. ConcurrentHashMap, AtomicLong, ReadWriteLock recipes
```

You will not have time to invent these. **Memorize them.**

---

## Universal Package Layout (Java)

```
src/main/java/com/example/system/
├── Main.java                    ← composition root + demo
│
├── api/                         ← controllers / CLI / driver
│   └── OrderController.java
│
├── application/                 ← service layer (orchestration)
│   ├── OrderService.java
│   ├── DispatchService.java
│   └── command/
│       └── PlaceOrderCommand.java
│
├── domain/                      ← entities, value objects, enums
│   ├── Order.java
│   ├── OrderItem.java
│   ├── OrderStatus.java
│   ├── Money.java
│   └── exception/
│       └── DomainException.java
│
├── repository/                  ← persistence interfaces + impl
│   ├── OrderRepository.java
│   └── InMemoryOrderRepository.java
│
├── strategy/                    ← pluggable algorithms
│   ├── PricingStrategy.java
│   ├── StandardPricing.java
│   └── SurgePricing.java
│
├── event/                       ← domain events + bus
│   ├── DomainEvent.java
│   ├── EventBus.java
│   └── handler/
│       └── NotifyHandler.java
│
└── concurrency/
    └── IdempotencyStore.java
```

The whole system fits in 8 packages. The interviewer can navigate it in 30 seconds.

---

## The 90-Minute Build Plan

```
0:00–0:10   Read prompt, ask 3 clarifying questions, write FR
0:10–0:15   Sketch the class diagram on paper / a comment block
0:15–0:25   Create skeleton (interfaces, packages)
0:25–0:55   Implement happy path
0:55–1:10   Implement 1–2 edge cases (idempotency, state validation)
1:10–1:20   Add a Strategy or factory to show extensibility
1:20–1:25   Write 2–3 JUnit tests
1:25–1:30   Run, demo via Main.java, talk through tradeoffs
```

Adjust for 60-min or 120-min variants.

---

## Step 1: Clarify (10 min)

Same as LLD, but compressed:

```
- Inputs: how is the system invoked? (CLI? HTTP? in-memory?)
- Persistence: in-memory OK? File? DB?
- Concurrency: single-thread or multi-thread?
- Extensibility hints: what new behavior should be easy to add?
```

In 90% of machine coding rounds, the answer is:
- **CLI / Main** invocation.
- **In-memory** persistence.
- **No concurrency** required (but "would be nice" if you handle it).
- **Strategy / pattern showcase** is implicitly expected.

---

## Step 2: Sketch (5 min)

In a comment block at the top of `Main.java`:

```java
/*
 * SKETCH
 * ──────────────────────────────────────────────
 *   OrderService ──▶ OrderRepository
 *      │
 *      ├── PricingService ──▶ PricingStrategy*
 *      │                       ├── StandardPricing
 *      │                       └── SurgePricing
 *      │
 *      └── DispatchService ──▶ DispatchStrategy
 *
 * Lifecycle:
 *   PLACED → CONFIRMED → PREPARING → READY → OUT → DELIVERED
 *                  └────────────────────────────► CANCELLED
 *
 * Threading:
 *   - Repository uses ConcurrentHashMap.
 *   - Order has version field; updates are CAS-style.
 */
```

This anchors your design and serves as documentation.

---

## Step 3: Skeleton (10 min)

Create all packages and stub classes. Compile early, compile often.

```java
// domain/Order.java
public class Order {
  private final UUID id;
  private OrderStatus status;
  private final List<OrderItem> items;
  private long version;
  // ...
}
```

```java
// repository/OrderRepository.java
public interface OrderRepository {
  Optional<Order> findById(UUID id);
  Order save(Order o);
}

// repository/InMemoryOrderRepository.java
public class InMemoryOrderRepository implements OrderRepository {
  private final Map<UUID, Order> store = new ConcurrentHashMap<>();
  public Optional<Order> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
  public Order save(Order o) { store.put(o.id(), o); return o; }
}
```

Get from skeleton to compiling in 10 minutes. Then iterate.

---

## Step 4: Happy Path (30 min)

Implement the **single most important flow** end-to-end.

### Rules

- One method does one thing.
- All shared state goes through repositories.
- Throw **specific** exceptions, not `RuntimeException`.
- Validate at the boundary (controller / API).
- Domain throws on invariant violation.

### Example

```java
public Order placeOrder(PlaceOrderCommand cmd) {
  validate(cmd);                                          // application
  Order o = idem.findExisting(cmd.idempotencyKey())       // idempotency
                .orElse(null);
  if (o != null) return o;
  Money total = pricing.compute(cmd.items());             // strategy
  o = new Order(cmd, total);
  o = orders.save(o);                                     // persist
  events.publish(new OrderPlaced(o.id()));                // event bus
  return o;
}
```

Each line tells a story. No deep logic in the orchestrator.

---

## Step 5: Edge Cases (15 min)

Pick **2–3** representative edge cases. Don't try to handle everything.

| Edge case | Why it matters |
| --- | --- |
| Idempotent retry | Demonstrates concurrency awareness |
| Invalid state transition | Demonstrates state machine awareness |
| Empty / nil input | Demonstrates input validation |
| Out of stock / no driver | Demonstrates business rule handling |

### Example

```java
public void cancel(UUID orderId) {
  Order o = orders.findById(orderId).orElseThrow(NotFoundException::new);
  if (!o.canCancel()) throw new IllegalStateException("Cannot cancel in state " + o.status());
  o.cancel();
  orders.save(o);
  events.publish(new OrderCancelled(o.id()));
}
```

---

## Step 6: Show Extensibility (10 min)

The interviewer wants to see one thing **vary**:

- A new pricing strategy
- A new dispatch algorithm
- A new payment method

Add **two** strategies and a factory.

```java
public interface PricingStrategy { Money compute(List<OrderItem> items); }

public class StandardPricing implements PricingStrategy { ... }

public class SurgePricing implements PricingStrategy {
  private final double factor;
  public SurgePricing(double factor) { this.factor = factor; }
  public Money compute(List<OrderItem> items) {
    return new StandardPricing().compute(items).multiply(factor);
  }
}
```

Wire with a Map or factory:

```java
PricingStrategy pricing = surgeActive ? new SurgePricing(1.5) : new StandardPricing();
```

State the OCP win: "Adding a new pricing model = adding a class, not editing existing ones."

---

## Step 7: Tests (5–10 min)

Two or three meaningful tests beat ten shallow ones.

```java
@Test void placeOrder_persists_and_returnsOrder() {
  Order out = service.placeOrder(cmd("idem-1", item("m_9", 2)));
  assertThat(out.status()).isEqualTo(OrderStatus.PLACED);
  assertThat(repo.findById(out.id())).isPresent();
}

@Test void placeOrder_isIdempotent() {
  Order a = service.placeOrder(cmd("idem-1", item("m_9", 2)));
  Order b = service.placeOrder(cmd("idem-1", item("m_9", 2)));
  assertThat(a.id()).isEqualTo(b.id());
}

@Test void cancel_inDeliveredState_throws() {
  Order o = anOrderInState(OrderStatus.DELIVERED);
  assertThatThrownBy(() -> service.cancel(o.id()))
    .isInstanceOf(IllegalStateException.class);
}
```

Show: happy path, concurrency invariant, state-machine invariant.

---

## Step 8: Demo (5 min)

Make `Main.java` runnable. The interviewer will run it.

```java
public static void main(String[] args) {
  // composition root
  OrderRepository orders = new InMemoryOrderRepository();
  IdempotencyStore idem = new InMemoryIdempotencyStore();
  EventBus events = new InMemoryEventBus();
  events.register(OrderPlaced.class, e -> System.out.println("event: " + e));

  PricingStrategy pricing = new StandardPricing();
  OrderService svc = new OrderService(orders, idem, pricing, events);

  // demo
  Order o = svc.placeOrder(new PlaceOrderCommand("u1", "r1",
              List.of(new LineItem("m_9", 2)), "demo-key"));
  System.out.println("Placed: " + o);

  Order again = svc.placeOrder(new PlaceOrderCommand("u1", "r1",
              List.of(new LineItem("m_9", 2)), "demo-key"));
  System.out.println("Idempotent: " + (o.id().equals(again.id())));
}
```

---

## Quality Bar

Use this checklist while coding:

```
[ ] No public field; only methods.
[ ] No mutation outside of domain methods.
[ ] No null returns; use Optional or throw.
[ ] No magic numbers; named constants.
[ ] No string-typed enums; use Enum.
[ ] No shared mutable state without ConcurrentHashMap / AtomicReference / lock.
[ ] No 200-line method; max ~30 lines.
[ ] Validation at boundary, business rules in domain.
[ ] Each public method has a single purpose stated by its name.
```

---

## Common Pitfalls

| Pitfall | Why it hurts |
| --- | --- |
| Spending 30 minutes on Maven | Run out of time |
| Building a UI when CLI suffices | Distraction |
| Premature optimization | Distraction |
| Adding 5 patterns "to look senior" | Confuses |
| All in `Main.java` | No layering |
| `HashMap` for shared state | Race condition |
| `synchronized` everywhere | Slow, error-prone |
| Catching `Exception` and swallowing | Hides bugs |
| Finishing without tests | Looks unprofessional |

---

## Reusable Templates

### Idempotency store

```java
public class InMemoryIdempotencyStore {
  private final Map<String, UUID> map = new ConcurrentHashMap<>();
  public Optional<UUID> get(String key) { return Optional.ofNullable(map.get(key)); }
  public boolean put(String key, UUID resourceId) {
    return map.putIfAbsent(key, resourceId) == null;
  }
}
```

### Versioned save

```java
public Order save(Order o) {
  store.compute(o.id(), (k, existing) -> {
    if (existing != null && existing.version() != o.version() - 1)
      throw new OptimisticLockException();
    return o;
  });
  return o;
}
```

### Simple event bus

```java
public class InMemoryEventBus {
  private final Map<Class<?>, List<Consumer<?>>> subs = new ConcurrentHashMap<>();
  public <E> void register(Class<E> t, Consumer<E> h) {
    subs.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>()).add(h);
  }
  @SuppressWarnings("unchecked")
  public <E> void publish(E event) {
    subs.getOrDefault(event.getClass(), List.of())
        .forEach(h -> ((Consumer<E>) h).accept(event));
  }
}
```

### Money value object

```java
public final class Money {
  private final BigDecimal amount;
  private final String currency;
  public Money(BigDecimal a, String c) { this.amount = a; this.currency = c; }
  public Money add(Money o) { check(o); return new Money(amount.add(o.amount), currency); }
  public Money multiply(BigDecimal f) { return new Money(amount.multiply(f), currency); }
  private void check(Money o) {
    if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
  }
  // equals, hashCode, toString
}
```

---

## What "Done" Looks Like

A 90-minute build that ends with:

- Compiles.
- Runs from `Main.java`.
- Has 3+ JUnit tests passing.
- Has 2+ implementations of a strategy (extensibility shown).
- Has idempotency and one state-machine validation.
- Has a 1-page README explaining how to run + design choices.

If you can deliver that consistently, you'll pass machine coding rounds at any product company.

---

## Checklist

- [ ] Skeleton compiled within 15 minutes.
- [ ] Happy path runnable end-to-end from `Main`.
- [ ] At least 1 strategy with 2 implementations.
- [ ] Idempotency demonstrated.
- [ ] At least 1 state-machine invariant tested.
- [ ] Repository in-memory + interface.
- [ ] All shared state thread-safe.
- [ ] 3 unit tests.
- [ ] README updated with run instructions.
