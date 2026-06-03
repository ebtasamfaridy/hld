# 14 · Splitwise — Interviewer Follow-ups

> 90 seconds, out loud.

---

## Q1. "Walk me through how you'd model an expense."

> An `Expense` is the central aggregate. It has:
> - `payers` — list of (user, amount) tuples (one or more people paid).
> - `shares` — list of (user, owedAmount) tuples (the split).
>
> Both lists must sum to `expense.amount`. The `splitMethod` (EQUAL, EXACT, PERCENT, SHARE, ITEM_WISE, ADJUSTMENT) drives a Strategy that computes shares from input config.
>
> Currency is fixed per expense. Idempotency key prevents duplicate creates.
>
> Audits capture before/after for every edit/delete. Balances are computed downstream from expense events.

---

## Q2. "How do you split equally if the total doesn't divide cleanly?"

> Work in integer cents. For ₹100 split among 3:
>
> ```
> total_cents = 10000
> per_each = 10000 / 3 = 3333
> remainder = 10000 - 3333*3 = 1
> ```
>
> The first participant in canonical order gets the extra cent. Participants 1 and 2 get 33.33; participant 3 also gets 33.34 — sums to 100.00 exactly.
>
> Determinism is critical — same input always yields same shares. Otherwise audits would diverge across servers.

---

## Q3. "How do you keep balances consistent when many expenses are added concurrently?"

> Balance is a derived view, updated asynchronously from expense events. Architecture:
>
> 1. Expense create writes `expense + outbox` in one TX.
> 2. Outbox publishes `ExpenseCreated` to Kafka, partitioned by `group_id`.
> 3. Each partition has one consumer thread; events processed in order.
> 4. Consumer applies deltas to `pair_balances` rows.
> 5. Each event has `event_id`; pair_balance row tracks `last_event_id` to dedup.
>
> Within a partition, no contention. Across partitions, no shared state.

---

## Q4. "Walk me through debt simplification."

> Goal: minimize cash transfers in a group.
>
> 1. Compute net balance per user: positive = should receive, negative = should pay.
> 2. Use min-cash-flow heuristic:
>    - Pop max debtor (most negative) and max creditor (most positive).
>    - Transfer `min(|debtor|, creditor)` from debtor to creditor.
>    - Re-insert remainders.
>    - Repeat until all zero.
>
> Result: at most N-1 transfers, often fewer. Optimal NP-hard but heuristic is good enough in practice.
>
> Output is **suggestions**; users record actual settlements when paid.

---

## Q5. "What if a user edits an expense after balances have been computed?"

> The edit endpoint:
>
> 1. Loads expense with current version.
> 2. Validates new payload.
> 3. Computes new shares.
> 4. Writes new expense row + audit (with before/after) + outbox event.
> 5. The `ExpenseEdited` event carries both old and new shares + payers.
> 6. Balance Service consumes: reverses old deltas, applies new deltas.
>
> Optimistic version locks prevent concurrent edits.

---

## Q6. "How do you handle multi-currency?"

> Each expense has its own currency. Balances are stored per (pair, group, currency). We never convert in storage.
>
> At display time, the user's `homeCurrency` and a daily FX snapshot are used to show "approx total." Underlying debts remain in original currencies.
>
> This is the only honest approach: a $100 debt today must remain a $100 debt regardless of FX moves.

---

## Q7. "Two payers for the same expense, three participants — how do shares work?"

> Suppose Alice pays ₹600 and Bob pays ₹400 for an expense of ₹1000, equally split among Alice, Bob, Carol (so each owes ₹333.33).
>
> Per-pair allocation: each participant's owed amount is distributed across payers proportionally.
>
> ```
> Carol owes 333.33; allocate to Alice (600/1000) = 200, to Bob (400/1000) = 133.33
> Bob   owes 333.33; allocate to Alice (600/1000) = 200, to Bob (400/1000) = 133.33 (self -> ignore)
> Alice owes 333.33; allocate to Alice (self -> ignore), to Bob (400/1000) = 133.33
> ```
>
> Net pair balances: Carol owes Alice 200, Carol owes Bob 133.33, Bob owes Alice 200, Alice owes Bob 133.33.
>
> Or simplified: Carol owes Alice 200 + Bob 133.33; Alice/Bob's mutual debt nets to 66.67 (Bob owes Alice net).

---

## Q8. "How do you scale this for 100M users?"

> 1. **Stateless services**: scale horizontally.
> 2. **Postgres partitioned by month** for expenses, audits, settlements.
> 3. **Sharded by group_id** for write throughput; balance partitioned by `min_user_id` for non-group.
> 4. **Redis Cluster** for balance cache.
> 5. **Per-region stacks**.
> 6. **Activity feed via separate ES / Cassandra cluster** (eventual).

---

## Q9. "How do you ensure no group ever has imbalanced books?"

> The fundamental invariant: `sum of pair balances per group per currency = 0`.
>
> Defenses:
>
> 1. Domain validation: `sum(payers) = sum(shares) = total` for each expense.
> 2. Event-driven: balances apply deterministic deltas, so they can't drift if events apply correctly.
> 3. Reconciliation cron nightly: sums all pair balances per group per currency, alerts SRE on drift > ₹0.01.
>
> If we ever doubt the snapshot, recompute from event log — the source of truth.

---

## Q10. "What happens when you delete an expense that's been partially settled?"

> Settlement reduces overall pair balance, not specific expenses. So:
>
> - Delete the expense → balance reverses.
> - Settlement remains.
> - Net balance changes (may now be positive or different sign).
>
> User is notified. They can adjust by adding new expenses or recording reverse settlements.
>
> We **don't** prevent delete-after-settle because users sometimes legitimately need to correct old data. The audit trail keeps everything traceable.

---

## Q11. "How would you implement search ('find all expenses involving Alice and Bob with amount > ₹500')?"

> Elasticsearch index updated via CDC from Postgres. Specification pattern in the API:
>
> ```java
> spec = involvesUser(alice).and(involvesUser(bob)).and(amountGreaterThan(500));
> ```
>
> Translates to ES query. Separate index for activity feed performance. Eventual consistency is fine.

---

## Q12. "Walk me through how a new split method (e.g., FRACTIONAL) would be added."

> 1. Add `FRACTIONAL` to `SplitMethod` enum.
> 2. Implement `class FractionalSplit implements SplitStrategy { compute(...) }`.
> 3. Update `SplitStrategyFactory` to map FRACTIONAL → FractionalSplit.
> 4. Add validation in API.
>
> No changes to balance, settlements, or any other code. **Open/Closed in action.**

---

## Q13. "What's your idempotency strategy?"

> - `Idempotency-Key` header on `POST /expenses`, `POST /settlements`.
> - DB UNIQUE on `idempotency_key` columns.
> - Replays return the original response.
> - Different payload + same key → 409.
> - Internal events have `event_id`; consumers dedup with `last_event_id` per pair_balance row.

---

## Q14. "How would you handle a recurring monthly rent expense?"

> Add `RecurringExpense` aggregate with cron-like schedule. A scheduler service triggers expense creation on schedule by calling `ExpenseService.create()` with a deterministic `idempotency_key` like `recurring:<id>:<2025-05>`.
>
> The user can edit the recurring template (changes future runs) or pause/cancel.

---

## Q15. "Where does this design hit its limits?"

> Three places:
>
> 1. **Cross-region groups** — when members live in different DB regions; some compromise on latency or eventual consistency.
> 2. **Very large groups** (1000+ members) — debt simplification still O(N log N), but UI of N pair balances is ugly. Need group summary views.
> 3. **Real-time auto-pay** — adding bank integrations adds dual writes, regulatory hurdles, and reversal flows.

---

Practice each. The Splitwise system is algorithmically rich (split + simplify) — show command of both.
