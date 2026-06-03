# 05 · Splitwise — Database Design

## Schemas

### Users + Friends

```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  name          VARCHAR(120) NOT NULL,
  email         VARCHAR(120) UNIQUE NOT NULL,
  phone         VARCHAR(20),
  home_currency CHAR(3) NOT NULL DEFAULT 'INR',
  created_at    TIMESTAMPTZ DEFAULT now()
);

-- canonical pair: user_a < user_b lexicographically
CREATE TABLE friendships (
  user_a    UUID NOT NULL REFERENCES users(id),
  user_b    UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_a, user_b),
  CONSTRAINT chk_canonical CHECK (user_a < user_b)
);
```

### Groups

```sql
CREATE TABLE groups (
  id          UUID PRIMARY KEY,
  name        VARCHAR(120) NOT NULL,
  type        VARCHAR(20) NOT NULL,   -- TRIP, HOME, COUPLE, OTHER
  created_by  UUID NOT NULL REFERENCES users(id),
  closed      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE group_members (
  group_id    UUID NOT NULL REFERENCES groups(id),
  user_id     UUID NOT NULL REFERENCES users(id),
  joined_at   TIMESTAMPTZ DEFAULT now(),
  removed_at  TIMESTAMPTZ,
  PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_gm_user_active ON group_members (user_id) WHERE removed_at IS NULL;
```

### Expenses

```sql
CREATE TABLE expenses (
  id              UUID PRIMARY KEY,
  group_id        UUID REFERENCES groups(id),    -- nullable for non-group
  created_by      UUID NOT NULL REFERENCES users(id),
  description     VARCHAR(500),
  amount          NUMERIC(14,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  split_method    VARCHAR(20) NOT NULL,
  metadata        JSONB,
  idempotency_key VARCHAR(80) UNIQUE,
  version         BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_status CHECK (status IN ('ACTIVE','EDITED','DELETED'))
) PARTITION BY RANGE (created_at);

-- monthly partitions
CREATE TABLE expenses_2025_05 PARTITION OF expenses FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');

CREATE INDEX idx_expenses_group ON expenses (group_id, created_at DESC) WHERE status='ACTIVE';
CREATE INDEX idx_expenses_creator ON expenses (created_by, created_at DESC) WHERE status='ACTIVE';
```

### Expense participants (payers + shares unified or split)

```sql
-- Two roles: paid (positive amount they paid) and owed (their share)
CREATE TABLE expense_participants (
  expense_id   UUID NOT NULL REFERENCES expenses(id),
  user_id      UUID NOT NULL,
  paid_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,    -- portion they actually paid
  owed_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,    -- their share of the bill
  PRIMARY KEY (expense_id, user_id)
);
```

A user might both pay and owe (paid 1000, owes 200 of it). We store both columns.

Invariant: `sum(paid_amount) == expense.amount` and `sum(owed_amount) == expense.amount`.

### Expense edits / audits

```sql
CREATE TABLE expense_audits (
  id           BIGSERIAL,
  expense_id   UUID NOT NULL,
  actor_id     UUID NOT NULL,
  action       VARCHAR(20) NOT NULL,    -- CREATED, EDITED, DELETED
  before_json  JSONB,
  after_json   JSONB,
  occurred_at  TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (occurred_at, id)
) PARTITION BY RANGE (occurred_at);
```

### Settlements

```sql
CREATE TABLE settlements (
  id              UUID PRIMARY KEY,
  payer_id        UUID NOT NULL REFERENCES users(id),
  payee_id        UUID NOT NULL REFERENCES users(id),
  group_id        UUID REFERENCES groups(id),
  amount          NUMERIC(14,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  method          VARCHAR(20),
  status          VARCHAR(20) NOT NULL DEFAULT 'RECORDED',
  settled_at      TIMESTAMPTZ NOT NULL,
  idempotency_key VARCHAR(80) UNIQUE,
  version         BIGINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT chk_status CHECK (status IN ('RECORDED','DISPUTED','REVERSED'))
);

CREATE INDEX idx_settle_pair ON settlements (payer_id, payee_id, created_at DESC);
CREATE INDEX idx_settle_group ON settlements (group_id, created_at DESC);
```

### Pair balances (snapshot)

```sql
CREATE TABLE pair_balances (
  user_a    UUID NOT NULL,
  user_b    UUID NOT NULL,
  group_id  UUID,                       -- null for "all groups summary"
  currency  CHAR(3) NOT NULL,
  net_amount NUMERIC(14,2) NOT NULL,    -- positive: A owes B
  last_event_id UUID,                   -- last expense/settlement applied
  updated_at  TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_a, user_b, group_id, currency),
  CONSTRAINT chk_canonical CHECK (user_a < user_b)
);
```

We store **per-pair, per-group, per-currency** rows. The "overall" balance is the per-pair row with `group_id = NULL`.

### Outbox

Same pattern.

---

## Locking strategy

| Concern | Strategy |
| --- | --- |
| Expense create | Idempotency key UNIQUE; insert + outbox in TX |
| Expense edit / delete | Optimistic via `version` |
| Settlement | Idempotency key UNIQUE |
| Balance update | Single-writer worker per (user_a, user_b, group, currency) |
| Group close with active balances | Pre-check; reject if any non-zero |

### Why single-writer for balances?

The Kafka topic for `ExpenseCreated` is partitioned by `group_id`. Within a partition, events are ordered. The Balance worker per partition processes them sequentially; balance rows are updated without contention.

For per-pair non-group balances, we partition by `min(userA, userB)`'s hash so they always go to the same worker.

---

## Computing balances from event log

We can always **recompute** a balance by replaying the event log:

```sql
SELECT
  e.currency,
  SUM(CASE WHEN ep.user_id = $A THEN ep.paid_amount - ep.owed_amount ELSE 0 END) -
  SUM(CASE WHEN ep.user_id = $B THEN ep.paid_amount - ep.owed_amount ELSE 0 END)
  AS a_minus_b
FROM expense_participants ep
JOIN expenses e ON e.id = ep.expense_id
WHERE e.status='ACTIVE' AND e.group_id = $G
  AND ($A IN (SELECT user_id FROM expense_participants WHERE expense_id = ep.expense_id))
  AND ($B IN (SELECT user_id FROM expense_participants WHERE expense_id = ep.expense_id))
GROUP BY e.currency;
```

Then subtract settlements:

```sql
SELECT currency,
       SUM(CASE WHEN payer_id=$A AND payee_id=$B THEN amount ELSE -amount END)
FROM settlements
WHERE status='RECORDED' AND group_id=$G
  AND ((payer_id=$A AND payee_id=$B) OR (payer_id=$B AND payee_id=$A))
GROUP BY currency;
```

This is the source of truth. The `pair_balances` table is a denormalized cache.

---

## Sample queries

```sql
-- Per-friend balance summary
SELECT pb.user_b, pb.currency, pb.net_amount FROM pair_balances pb
WHERE pb.user_a = ($me_canonical_a) AND pb.group_id IS NULL;

-- Group balances for all members
SELECT pb.user_a, pb.user_b, pb.currency, pb.net_amount FROM pair_balances pb
WHERE pb.group_id = $group;

-- Activity feed (last 50 expenses involving me)
SELECT e.* FROM expenses e
JOIN expense_participants ep ON ep.expense_id = e.id
WHERE ep.user_id = $me AND e.status='ACTIVE'
ORDER BY e.created_at DESC LIMIT 50;
```

---

## Why we don't store pair_balances inline with expenses

Two reasons:
1. **Concurrency**: many expenses hit the same pair simultaneously. Inline updates require optimistic locking on the pair row.
2. **Consistency**: balance is derived. If we ever doubt the snapshot, we recompute from the log.

Splitting write (expense) and aggregate (balance) gives us a clean event-driven architecture and full auditability.
