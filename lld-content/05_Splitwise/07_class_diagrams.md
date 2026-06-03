# 07 · Splitwise — Class Diagrams

## Domain

```mermaid
classDiagram
  class Expense {
    -UUID id
    -UUID groupId
    -UUID createdBy
    -String description
    -Money amount
    -Currency currency
    -SplitMethod method
    -List~Payer~ payers
    -List~ExpenseShare~ shares
    -ExpenseStatus status
    -long version
    +edit(EditExpenseCommand)
    +delete()
  }

  class Payer { <<value>> }
  class ExpenseShare { <<value>> }
  class SplitMethod { <<enumeration>> }

  Expense ..> Payer
  Expense ..> ExpenseShare
  Expense ..> SplitMethod
```

```mermaid
classDiagram
  class Settlement {
    -UUID id
    -UUID payerId
    -UUID payeeId
    -UUID groupId
    -Money amount
    -SettlementMethod method
    -SettlementStatus status
    +reverse()
    +dispute()
  }

  class SettlementMethod { <<enumeration>> }
  class SettlementStatus { <<enumeration>> }
```

```mermaid
classDiagram
  class Group {
    -UUID id
    -String name
    -GroupType type
    -List~UUID~ memberIds
    -boolean closed
    +addMember(userId)
    +removeMember(userId)
    +close()
  }
  class GroupType { <<enumeration>> }
```

```mermaid
classDiagram
  class User { -UUID id; -String name; -Currency homeCurrency }
  class Friendship { -UUID userA; -UUID userB }
```

---

## Application services

```mermaid
classDiagram
  class ExpenseService {
    -ExpenseRepository repo
    -GroupRepository groups
    -SplitStrategyFactory factory
    -EventPublisher events
    -IdempotencyStore idem
    +create(CreateExpenseCommand)
    +edit(EditExpenseCommand)
    +delete(UUID)
  }

  class SettlementService {
    -SettlementRepository repo
    -EventPublisher events
    +record(RecordSettlementCommand)
    +reverse(UUID)
    +dispute(UUID)
  }

  class BalanceService {
    -PairBalanceRepository pairs
    -RedisCache cache
    -DebtSimplifier simplifier
    +getFriend(user, friend)
    +getGroup(groupId, asUser)
    +getOverall(user)
    +simplifyGroup(groupId)
    +applyExpense(ExpenseEvent)
    +applySettlement(SettlementEvent)
  }

  class GroupService { ... }
  class FriendService { ... }
```

---

## Strategies

```mermaid
classDiagram
  class SplitStrategy {
    <<interface>>
    +compute(Money total, List~UUID~ participants, Map config) List~ExpenseShare~
  }

  class EqualSplit
  class ExactSplit
  class PercentSplit
  class ShareSplit
  class ItemWiseSplit
  class AdjustmentSplit

  SplitStrategy <|.. EqualSplit
  SplitStrategy <|.. ExactSplit
  SplitStrategy <|.. PercentSplit
  SplitStrategy <|.. ShareSplit
  SplitStrategy <|.. ItemWiseSplit
  SplitStrategy <|.. AdjustmentSplit

  class SplitStrategyFactory {
    +of(SplitMethod) SplitStrategy
  }
```

---

## Repositories

```mermaid
classDiagram
  class ExpenseRepository {
    <<interface>>
    +findById(UUID)
    +save(Expense)
    +findByIdempotencyKey(String)
    +findByGroup(UUID groupId)
  }

  class SettlementRepository { <<interface>> }
  class PairBalanceRepository {
    <<interface>>
    +get(userA, userB, groupId, currency)
    +increment(userA, userB, groupId, currency, delta)
  }
  class GroupRepository
  class UserRepository
```

---

## Debt simplification

```mermaid
classDiagram
  class DebtSimplifier {
    +simplify(Map~UUID, Long~ netBalances) List~Transfer~
  }

  class Transfer { <<value>> -UUID from; -UUID to; -Money amount }
```

---

## State pattern (Expense — light)

Most state behavior is in `status` (ACTIVE → DELETED). We use enum + transition map; the differences are small enough.

For `Settlement` (RECORDED → DISPUTED → REVERSED), same approach.

---

## Layering

```mermaid
flowchart LR
  subgraph api
    ExpenseController; SettlementController; BalanceController; GroupController
  end

  subgraph application
    ExpenseService; SettlementService; BalanceService; GroupService
  end

  subgraph domain
    Expense; Settlement; Group; SplitStrategy; DebtSimplifier
    ExpenseRepository_int[<<interface>> ExpenseRepository]
  end

  subgraph infra
    JpaExpenseRepository; KafkaPublisher; RedisCache
  end

  api --> application
  application --> domain
  application --> infra
  infra --> domain
```

---

## Take-aways

- **Strategy** for split methods is the most-used pattern.
- Balance is a derived view — kept as cache + snapshot.
- **DebtSimplifier** is a focused algorithm class.
- Idempotent expense creation via key.
- All cross-aggregate communication via events.
