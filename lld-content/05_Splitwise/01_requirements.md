# 01 · Splitwise — Requirements

## Problem statement

Design a Splitwise-like platform: users record shared expenses (dinner, rent, trip), split them in various ways, track who owes whom, and settle up.

We design the **backend**.

---

## Functional requirements

### Core (in scope)

**User-facing**
- Sign up / log in.
- Add a friend (by email/phone).
- Create a group with members.
- Record an expense in a group:
  - paid by 1 or more people (with split among payers if needed)
  - split among one or more people
  - split modes: EQUAL, EXACT, PERCENT, SHARE, ITEM_WISE, ADJUSTMENT
- Edit / delete an expense (with audit).
- Add comments / receipt photo to an expense.
- Record a payment (settlement) between two users.
- View balances:
  - per-friend (you owe X / X owes you)
  - per-group
  - overall
- See debt simplification suggestions (minimum transfers).
- Multi-currency: each expense in its own currency; user's home currency for summary.
- Notifications when added to expenses or groups.

**Platform-facing**
- Maintain group invariants (sum of net balances per group = 0).
- Compute debt-simplification graph on demand (per group).
- Activity feed.
- Audit history of every expense / settlement.

### Extensions (acknowledged, not built)

- Splitwise Pro features (charts, search, automatic recurring expenses).
- OCR for receipts.
- Bank integrations.
- True debt-simplification with payment auto-routing.
- Group expense limits / approvals.

### Out of scope

- Mobile apps.
- Identity provider internals.
- Push provider internals.
- Currency exchange-rate sourcing (we use a daily snapshot).

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Add expense p99 | < 200 ms | Snappy UX |
| Read balances p99 | < 100 ms | Mostly cached |
| Strong consistency | per-expense and per-balance | Money |
| Activity scale | 100 M users, 1 B expenses (5 yr) | Scale |
| Throughput | ~5 K expenses/sec peak | Tail trip-event peaks |
| Audit | every change logged | Disputes |
| Multi-currency | yes | Real-world groups travel |

---

## Actors

```
User              - the only real actor
Group             - many users
Friend            - implicit relation
Expense           - the central event
Settlement        - a recorded payment
Notification      - notifies parties
Currency / FX     - external; daily snapshot
```

---

## Edge cases

| Case | Handling |
| --- | --- |
| Floating-point split rounding | Track in cents/paise (BigDecimal). One participant gets the rounding remainder. |
| User edits an expense after others have viewed | Update + audit; recompute balances |
| User deletes an expense | Mark deleted; recompute balances |
| User added/removed from group with active balances | Cannot remove if non-zero balance to/from them |
| Payment recorded but later disputed | Mark settlement as disputed; recompute |
| Currency mismatch | Settlement is in expense currency; balances kept per currency |
| Floating debt loops (A owes B, B owes A) | Debt simplification minimizes |
| Concurrent edits | Optimistic locking on expense |
| Soft delete vs hard delete | Always soft; rebuild balances by replaying log |
| Negative shares (someone gets credit) | Allowed (e.g., loyalty discount) — design allows |
| Self-expense | Allowed (single-user expense, or `paid by me, owe myself` is no-op) |
| Add expense to closed group | Reject |

---

## Output

```
Actors:        User, Group, Expense, Settlement, NotificationService, CurrencyService
Core FR:       expense CRUD, splits, balances, debt simplify, settlement, multi-currency, audit
NFR:           strong on money, p99 add < 200ms, eventual on activity feed
Out of Scope:  mobile, IDP, FX sourcing
Extensions:    OCR, recurring, bank integration, auto-pay
```
