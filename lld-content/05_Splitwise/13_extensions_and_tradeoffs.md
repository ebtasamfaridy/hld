# 13 · Splitwise — Extensions & Tradeoffs

## Tradeoffs

### 1. Balance as a derived view (event-sourced)

**Alternative**: update balance inline with expense in same transaction.

**Chosen**: balance is consumed asynchronously from expense events.

**Why**:
- Audit trail is the source of truth.
- Easier to recompute / migrate.
- Balance writes scale independently.
- Refactor expense logic without touching balance code.

**Cost**: ~ms eventual consistency on balance display.

### 2. Per-pair, per-currency, per-group balance rows

**Alternative**: one global balance per pair.

**Chosen**: per-(pair, group, currency).

**Why**: groups need their own simplification; users may have currency-specific debts.

### 3. Multi-currency without auto-conversion

We never convert balances. A debt of $100 stays $100 forever; conversion is a display concern using a daily FX snapshot.

### 4. Integer cents internally

`Money` stores cents (long), avoids float drift. Arithmetic is exact.

### 5. Min-cash-flow heuristic for simplification

Optimal NP-hard. We use a heuristic that's optimal in practice, runs in O(N log N), and matches Splitwise's behavior.

### 6. State machines kept simple (enum + map)

Splitwise's lifecycles are 2-3 states. State pattern would be overkill.

### 7. Always soft delete

Hard delete loses audit trail. Soft delete + reverse balance.

### 8. Idempotency on every write

Mandatory for retried mobile clients on flaky networks.

### 9. Fork old/new in events

Edit/delete events carry both old and new state, so consumers don't query historic data.

### 10. No locks on balance during read

Balance reads serve from cache or snapshot. We accept minor staleness for low latency.

---

## V2 extensions

### A. Recurring expenses

A user sets up "₹2000 rent monthly, split equally among roommates."

**Design changes:**
- `RecurringExpense` aggregate: schedule (monthly, weekly, custom).
- Scheduler service spawns concrete `Expense` rows on schedule.
- The Expense pipeline is unchanged.

### B. OCR / receipt parsing

User uploads receipt; the system extracts items and amounts.

**Design changes:**
- Async OCR worker.
- Pre-fills the create-expense UI with parsed items.
- Item-wise split is a natural fit.

### C. Activity search

"Find all expenses where I was a payer for amounts > ₹500."

**Design**: Elasticsearch with CDC. Specification pattern composes filters.

### D. Bank integration / auto-pay

The biggest extension: when user records a settlement, actually move money via bank.

**Design changes:**
- `Settlement` becomes two-phase: PENDING → SETTLED via bank webhook.
- New `BankTransfer` aggregate.
- Refund flow if transfer fails.

This is V3 territory; many regulatory and security concerns.

### E. Group expense limits / approvals

Office team or large group: any expense > ₹5000 requires approval.

**Design changes:**
- `ApprovalRule` strategy per group.
- Expense state machine adds `PENDING_APPROVAL → APPROVED → ACTIVE`.
- Notify approvers; approval API.

### F. Smart splits

ML suggests splits based on past behavior ("you usually split groceries 60/40 with X").

**Design**: separate suggestion service consuming events; surfaces UI hints.

### G. Subscription tier (Splitwise Pro)

Charts, search, photos, no ads.

**Design**: feature flags + entitlement check at API gateway.

---

## Operational concerns

### Reconciliation

| Reconciliation | What |
| --- | --- |
| Sum of pair balances per group per currency = 0 | Strongest invariant |
| Cached balance vs DB snapshot | Drift detection |
| Expense participants sum = expense.amount | Double-check on every write |
| Settlements with deleted expenses | Audit only |

### Observability

- Expenses/sec, p99 create.
- Balance read p99 (cache hit rate).
- Simplification latency.
- Outbox lag.
- Currency distribution.

### Data retention

- Active expenses: forever (legal, audit).
- Audit log: 7 years.
- Activity feed: 2 years.

---

## What this design will NOT support without rework

- **Cryptocurrencies** — needs new asset model and pricing.
- **Real-time stock-style trading** — totally different domain.
- **Multi-tenancy SaaS for businesses** — adds workspace, billing, complex RBAC.

---

## Tradeoff summary table

| Choice | Picked | Alternative | Why |
| --- | --- | --- | --- |
| Balance | Derived from event log | Updated inline | Auditability, scale |
| Currency | Per-pair, no conversion | Convert on write | Truthful debts |
| Money | Long cents | BigDecimal/float | Precision |
| Simplification | Heuristic O(N log N) | NP-optimal | Practical |
| State | Enum + map | State pattern | Simple lifecycles |
| Delete | Soft | Hard | Audit |
| Idempotency | Always | Per-API | Reliability |
| Edit events | Carry old + new | Lookup history | Determinism |
| Settle | Recorded only | Auto-pay | V1 simplicity |
