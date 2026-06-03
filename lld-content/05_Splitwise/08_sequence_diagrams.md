# 08 · Splitwise — Sequence Diagrams

## 1. Create expense

```mermaid
sequenceDiagram
  autonumber
  participant U as User
  participant ES as ExpenseService
  participant SF as SplitStrategyFactory
  participant ER as ExpenseRepository
  participant DB as Postgres
  participant K as Kafka

  U->>ES: POST /expenses (idemKey, payload)
  ES->>ES: idempotency lookup
  ES->>ES: validate (group, participants, currency)
  ES->>SF: of(splitMethod) → strategy
  ES->>ES: shares = strategy.compute(total, participants, config)
  ES->>ES: validate sum(shares) == total
  ES->>DB: BEGIN
  ES->>DB: insert expenses
  ES->>DB: insert expense_participants × N
  ES->>DB: insert expense_audits CREATED
  ES->>DB: insert outbox(ExpenseCreated)
  ES->>DB: COMMIT
  ES-->>U: 201 {expense}
  Note over K: outbox -> Kafka publishes ExpenseCreated
```

---

## 2. Apply expense to balances (async)

```mermaid
sequenceDiagram
  participant K as Kafka
  participant BS as BalanceService
  participant R as Redis
  participant PB as PairBalanceRepository

  K->>BS: ExpenseCreated event
  loop for each pair (i, j) in participants
    BS->>BS: delta_ij = (paid_i - owed_i) per pair allocation
    BS->>PB: increment pair_balance (i, j, group, currency, delta)
    BS->>R: invalidate cached balances for i and j
  end
```

For per-pair allocation: when one person pays for many, the debt is "owed by each participant to the payer." We compute pair-level deltas:
```
For each participant p:
  For each payer q:
    delta(p->q) = participant.owedAmount × (payer.paidAmount / total)
```

A single user paying everything is the simple case: each participant owes the payer their share.

---

## 3. View balance (cached)

```mermaid
sequenceDiagram
  participant U as User
  participant BS as BalanceService
  participant R as Redis
  participant PB as PairBalanceRepository

  U->>BS: GET /balances/friend/{f}
  BS->>R: cache lookup
  alt hit
    R-->>BS: balance
  else miss
    BS->>PB: SELECT * FROM pair_balances WHERE user_a=min, user_b=max, group IS NULL
    PB-->>BS: rows
    BS->>R: cache
  end
  BS-->>U: 200 {balance}
```

---

## 4. Settlement

```mermaid
sequenceDiagram
  participant U as User
  participant SS as SettlementService
  participant DB as Postgres
  participant K as Kafka
  participant BS as BalanceService

  U->>SS: POST /settlements (idemKey)
  SS->>SS: validate balance covers + same currency
  SS->>DB: BEGIN
  SS->>DB: insert settlement row
  SS->>DB: insert outbox(SettlementRecorded)
  SS->>DB: COMMIT
  SS-->>U: 201
  Note over K: outbox publishes SettlementRecorded
  K->>BS: SettlementRecorded
  BS->>BS: pair_balance(payer, payee, group) -= amount
  BS->>R: invalidate cached balances
```

---

## 5. Edit expense

```mermaid
sequenceDiagram
  participant U as User
  participant ES as ExpenseService
  participant DB as Postgres
  participant K as Kafka
  participant BS as BalanceService

  U->>ES: PATCH /expenses/{id} (new payload)
  ES->>ES: load expense (with version)
  ES->>ES: compute new shares
  ES->>DB: BEGIN
  ES->>DB: insert expense_audits EDITED with before/after
  ES->>DB: update expense (version+1)
  ES->>DB: replace expense_participants
  ES->>DB: insert outbox(ExpenseEdited)
  ES->>DB: COMMIT
  Note over K: ExpenseEdited carries OLD and NEW shares
  K->>BS: ExpenseEdited
  BS->>BS: rollback OLD deltas
  BS->>BS: apply NEW deltas
  BS->>R: invalidate cached balances
```

The edit event carries both old and new state so balance updates are deterministic — no need to re-query the previous version.

---

## 6. Delete expense

```mermaid
sequenceDiagram
  participant U as User
  participant ES as ExpenseService
  participant K as Kafka
  participant BS as BalanceService

  U->>ES: DELETE /expenses/{id}
  ES->>ES: load (with version)
  ES->>DB: update expense set status='DELETED', version+1
  ES->>DB: insert outbox(ExpenseDeleted with shares snapshot)
  ES->>DB: COMMIT
  K->>BS: ExpenseDeleted
  BS->>BS: rollback the deltas previously applied
  BS->>R: invalidate
```

We always **soft-delete**. The expense row stays for audit. Balances are recomputed by reversing the prior deltas.

---

## 7. Debt simplification

```mermaid
sequenceDiagram
  participant U as User
  participant BS as BalanceService
  participant DS as DebtSimplifier

  U->>BS: GET /balances/group/{g}/simplify
  BS->>BS: load all pair_balances for group (per currency)
  BS->>BS: compute net per user (sum of receivables - payables)
  BS->>DS: simplify(netBalances)
  DS-->>BS: list of Transfer
  BS-->>U: 200 {transfers}
```

The algorithm runs at request time (not stored). For groups < 50 members, it's instant. For larger groups, we cache the result for 1 minute.

---

## 8. Group close with active balances

```mermaid
sequenceDiagram
  participant U as User
  participant GS as GroupService
  participant BS as BalanceService

  U->>GS: POST /groups/{id}:close
  GS->>BS: any non-zero pair_balance for this group?
  alt yes
    BS-->>GS: list pairs and amounts
    GS-->>U: 409 HAS_NONZERO_BALANCE
  else no
    GS->>GS: group.closed = true
    GS-->>U: 200
  end
```

We never silently delete data with money implications.

---

## What these reveal

- The Expense write is one transaction; balance update is async.
- All edit/delete events carry both old and new state for deterministic balance updates.
- Debt simplification is a stateless algorithm.
- Cache invalidation happens on every balance change.
- Idempotency keys + outbox = reliability.
