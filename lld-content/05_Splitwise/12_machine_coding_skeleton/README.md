# 12 · Machine Coding Skeleton — Splitwise

A focused Java skeleton.

## Layout

```
src/main/java/com/splitwise
├── Main.java
├── domain/             ← Expense, Settlement, Group, User, value objects
├── repository/         ← in-memory repos
├── service/            ← ExpenseService, BalanceService, SettlementService
├── split/              ← SplitStrategy + 6 implementations + factory
├── simplify/           ← DebtSimplifier
└── api/                ← optional CLI
```

## Demo flow

1. Seed users, group.
2. Add an expense (EQUAL split).
3. Add another expense (PERCENT split).
4. Add an expense (EXACT split).
5. Add an expense (SHARE split).
6. View balances per pair.
7. Run debt simplification.
8. Record a settlement; balances update.
9. Edit an expense; balances reverse + reapply.

## Highlights

- `SplitStrategy` interface + 4-6 implementations.
- `BalanceService` maintains per-pair balances.
- `DebtSimplifier` runs min-cash-flow.
