# 13 · Library — Extensions & Tradeoffs

## Tradeoffs we made

### 1. Book vs Copy

**Alternative**: One row per book with a `count` field.

**Chosen**: `Book` for catalog, `BookCopy` per physical instance.

**Why**: per-copy state (borrowed, lost, in-repair) is essential. A counter would lose this information.

### 2. Per-copy CAS (vs per-book lock)

**Alternative**: lock the Book row when borrowing.

**Chosen**: atomic UPDATE on a single Copy row.

**Why**: granular contention; multiple concurrent borrows for different copies of the same book don't conflict.

### 3. Reservations as a queue per book (not per copy)

A reservation says "I want any copy of this book." When a copy frees up, the head of the queue gets it.

This handles multi-copy libraries elegantly.

### 4. Reservation hold for 24 hours

Tradeoff between giving the member time to come pick up (24h) and not blocking the next person too long. We chose 24h.

### 5. Daily fine accrual (vs continuous)

Fines accumulate daily, not by the hour. Simpler, fairer, and matches typical library practice.

### 6. Fine as a separate aggregate

Fines have their own lifecycle (payment, waivers). Embedding them in the Loan would tangle financial state.

### 7. Member loan limit checked atomically

`UPDATE WHERE active_loan_count < limit` avoids the read-then-update race.

### 8. State stored explicitly (BORROWED vs OVERDUE)

We store OVERDUE rather than computing it. Pro: faster queries. Con: a cron must transition. We accept the cron.

---

## V2 extensions

### A. E-books / audiobooks

Different inventory model: licenses, not copies. A license can be loaned to N members concurrently; total = license limit.

**Design changes:**
- `Book.format` (PHYSICAL, EBOOK, AUDIOBOOK).
- `EbookLicense` aggregate with `concurrent_users` field.
- Borrow flow for ebooks decrements `concurrent_users` instead of changing copy status.
- "Return" is automatic at expiry.

The `Loan` aggregate is reused; just the inventory primitive differs.

### B. Inter-library loans (ILL)

A member at Library A wants a book that's only available at Library B (same chain or partner system).

**Design changes:**
- Cross-system reservation via API.
- Transfer task spans systems.
- Fine policies still per-loan.

### C. Recommendations

ML-driven: members who borrowed X also borrowed Y.

**Design**: separate analytical service consuming `LoanIssued` events. Recommendation API reads from the model.

### D. Member tiers

Students vs Faculty vs Public have different policies.

**Design**: BorrowPolicy strategy + factory. Already designed for this.

### E. Self-checkout via barcode

Member uses an app to scan a copy and check it out without librarian.

**Design**: dedicated mobile flow. Same `LoanService.borrow()` underneath. No changes to backend.

### F. Pre-paid fine wallet

Members can pre-load credits to auto-pay future fines.

**Design**: `FineWallet` aggregate. `FineService.pay()` checks wallet first.

### G. Multi-branch with central catalog

Already supported; the Book + Copy model accommodates many branches.

### H. Branch transfer workflow

A copy can be requested to be moved between branches (member request or admin).

**Design**: `TransferTask` aggregate; status (REQUESTED, IN_TRANSIT, ARRIVED).

---

## Operational concerns

### Observability

- Borrows/sec, returns/sec, reservations/sec.
- Overdue rate by branch.
- Average fine per member.
- Top-borrowed books.
- Stuck reservations (READY > expires_at + grace).
- Reservation queue depth distribution.

### Notifications

- 2 days before due → reminder.
- On due date → reminder.
- 1 day overdue → escalation.
- Reservation ready → "your book is available, pick up by X".
- Reservation about to expire → 1 hour warning.

### Reconciliation

| Reconciliation | What |
| --- | --- |
| Active loan count | sum of BORROWED+OVERDUE per member matches member.active_loan_count |
| Outstanding fines | sum of OUTSTANDING fines per member matches member.outstandingFineBalance |
| Copy status vs loan status | every BORROWED copy has exactly one active loan |
| Reservation queue order | queue_position consistency |

These run nightly. Drift triggers alerts.

---

## What this design will NOT support without rework

- **Multi-tenant library SaaS** — would need org-level isolation, billing, custom branding.
- **Subscriptions / KindleUnlimited-style models** — different licensing model.
- **Curated reading lists** — separate aggregate.

---

## Tradeoff summary table

| Choice | Picked | Alternative | Why |
| --- | --- | --- | --- |
| Inventory | Per-copy rows | Counter on Book | Per-copy state |
| Concurrency | Atomic CAS | Pessimistic lock | Granularity |
| Reservation | Per-book queue | Per-copy | Member-friendly |
| Hold duration | 24 h | 1 h / 7 d | Balance UX & throughput |
| Fine model | Daily accrual + idempotent cron | Continuous calculation | Simpler |
| State | Explicit OVERDUE | Computed | Index efficiency |
| Search | Postgres pg_trgm | Elasticsearch | Modest scale |
| Storage | Single Postgres | Sharded | Modest scale |
| Member tiers | Strategy | Inheritance | Cleaner |
| Fine | Separate aggregate | In Loan | Financial lifecycle |
