# 10 · Splitwise — Design Patterns

## 1. Strategy — Split methods

The signature pattern for Splitwise.

```java
public interface SplitStrategy {
  List<ExpenseShare> compute(Money total,
                             List<UUID> participants,
                             Map<String, Object> config);
}

class EqualSplit implements SplitStrategy { ... }
class ExactSplit implements SplitStrategy { ... }
class PercentSplit implements SplitStrategy { ... }
class ShareSplit implements SplitStrategy { ... }
class ItemWiseSplit implements SplitStrategy { ... }
class AdjustmentSplit implements SplitStrategy { ... }
```

A factory chooses the strategy:

```java
public class SplitStrategyFactory {
  public SplitStrategy of(SplitMethod method) {
    return switch (method) {
      case EQUAL      -> new EqualSplit();
      case EXACT      -> new ExactSplit();
      case PERCENT    -> new PercentSplit();
      case SHARE      -> new ShareSplit();
      case ITEM_WISE  -> new ItemWiseSplit();
      case ADJUSTMENT -> new AdjustmentSplit();
    };
  }
}
```

Adding a new split method = new class. The rest of the system is unchanged.

---

## 2. Strategy — Settlement methods (informational)

```java
public interface SettlementMethod {
  String label();
  boolean requiresExternalReference();
}
```

Currently: CASH, UPI, VENMO, BANK_TRANSFER, OTHER. If we ever integrate with a payment provider, we add an adapter for it.

---

## 3. Builder — Expense

```java
Expense e = Expense.builder()
              .group(groupId)
              .createdBy(userId)
              .description("Goa hotel")
              .amount(Money.inr(12000))
              .occurredAt(now)
              .splitMethod(SplitMethod.EQUAL)
              .payer(payerId, Money.inr(12000))
              .participants(List.of(u1, u2, u3))
              .idempotencyKey("...")
              .build();
```

Build validates:
- Sum of payers == amount.
- Sum of shares == amount (computed by strategy).
- All participants are valid group members.
- Single currency.

---

## 4. Repository

```java
public interface ExpenseRepository {
  Optional<Expense> findById(UUID);
  Expense save(Expense);
  Optional<Expense> findByIdempotencyKey(String);
  List<Expense> findByGroup(UUID groupId, Cursor cursor);
}
```

---

## 5. Observer / Pub-Sub — events

Outbox + Kafka. Every expense / settlement change publishes an event consumed by:
- Balance Service.
- Activity Feed Service.
- Notification Service.
- Analytics ETL.

Events carry the **before/after** state so consumers don't need to query the DB.

---

## 6. Command pattern

```java
public record CreateExpenseCommand(...) {}
public record EditExpenseCommand(UUID expenseId, ..., long expectedVersion) {}
public record DeleteExpenseCommand(UUID expenseId, long expectedVersion) {}
public record RecordSettlementCommand(...) {}
```

`expectedVersion` enables optimistic concurrency control.

---

## 7. Decorator — services

Tracing / metrics / logging wrappers.

---

## 8. Algorithm — DebtSimplifier

Encapsulated as its own class:

```java
public class DebtSimplifier {
  public List<Transfer> simplify(Map<UUID, Long> netBalanceCents);
}
```

Pure function: input → output. Easily unit-tested with stress tests on randomized graphs.

---

## 9. Adapter — Currency / FX

```java
public interface FxRateProvider {
  BigDecimal rate(String fromCurrency, String toCurrency, LocalDate date);
}

class DailySnapshotFxProvider implements FxRateProvider { ... }   // from a daily fetched table
class LiveFxProvider implements FxRateProvider { ... }            // calls an FX API
class StubFxProvider implements FxRateProvider { ... }            // for tests
```

Used only at the **display** layer. Balances are never converted at write time.

---

## 10. Chain of Responsibility — Expense validation

```java
List<ExpenseValidator> chain = List.of(
  new AuthValidator(),
  new GroupOpenValidator(),
  new MembersInGroupValidator(),
  new SingleCurrencyValidator(),
  new SumPayersEqualsTotalValidator(),
  new SplitConfigValidator(),    // method-specific
  new IdempotencyValidator()
);
```

Each fails with a typed error. Easy to add new rules.

---

## 11. Specification pattern (V2) — search filters

For activity feed search ("expenses where amount > 1000 AND I'm in"), specifications compose:

```java
Specification<Expense> spec = isInvolving(userId)
                                .and(amountGreaterThan(Money.inr(1000)))
                                .and(occurredAfter(date));
```

Translates to JPA Criteria or ES query. Useful when filters grow.

---

## 12. State pattern (light) — Settlement / Group

State pattern is overkill for Splitwise's small lifecycles. We use enum + map.

---

## 13. Memento — for undo

When editing or deleting, we store the prior state in `expense_audits.before_json`. Effectively the Memento pattern. This enables:
- Undo.
- Audit timeline.
- Recompute balances by replay.

---

## SOLID

- **S**: each strategy = one job.
- **O**: new split / settlement / FX = new class.
- **L**: every Strategy honors its interface.
- **I**: small repository interfaces.
- **D**: Services depend on `SplitStrategy`, `FxRateProvider`, `EventPublisher` — never concrete classes.

---

## Avoided patterns

- **Inheritance for split methods** (`EqualExpense extends Expense`) — would require N classes per split method × per group type. Strategy avoids the explosion.
- **Active record on Balance** — Balance is a derived view, not a domain entity with behavior. Treating it as one would tangle write-paths.
- **Singleton SplitStrategyFactory** — DI is cleaner.
