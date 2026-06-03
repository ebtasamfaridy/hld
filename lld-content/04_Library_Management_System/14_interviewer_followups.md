# 14 · Library — Interviewer Follow-ups

> 90 seconds, out loud.

---

## Q1. "Two members try to borrow the same copy at the same time. What happens?"

> Borrow uses an atomic SQL UPDATE:
>
> ```sql
> UPDATE book_copies SET status='BORROWED', version=version+1
> WHERE id=? AND status='AVAILABLE' AND version=?;
> ```
>
> One returns 1 row, the other 0. The losing transaction tries another available copy of the same book. If none, the second member is offered to reserve.
>
> The unique partial index `loans(copy_id) WHERE status IN ('BORROWED','OVERDUE')` is a belt-and-suspenders safety net.

---

## Q2. "Walk me through the Book vs Copy modeling decision."

> A `Book` is the catalog entry: title, ISBN, authors, genres. It's metadata. A `BookCopy` is a physical instance at a branch with its own status (AVAILABLE, BORROWED, IN_REPAIR, LOST, IN_TRANSIT).
>
> A library has many copies of one book. Without separation:
> - "Is this book available" requires summing borrows.
> - Tracking which physical copy is which is impossible.
> - Per-branch inventory becomes hacky.
>
> Borrow targets a specific copy with atomic CAS; the book itself doesn't change.

---

## Q3. "How does the reservation queue work?"

> A `Reservation` is per-book (not per-copy). When a member tries to borrow and no copy is available, we create a Reservation with `queue_position = current_max + 1`.
>
> When a copy returns, an event-driven worker picks the head of the queue:
>
> ```sql
> SELECT id FROM reservations
> WHERE book_id=? AND status='QUEUED'
> ORDER BY queue_position ASC
> FOR UPDATE SKIP LOCKED LIMIT 1;
> ```
>
> Then atomically holds the copy (`AVAILABLE → RESERVED_HOLD`), marks the reservation `READY` with `expires_at = now + 24h`, notifies the member.
>
> If they don't pick up by `expires_at`, a cron expires it (`READY → EXPIRED`), releases the held copy, and promotes the next.

---

## Q4. "What if a member has 4 active loans and the limit is 5; they fire 2 borrow requests in parallel?"

> Each request's atomic UPDATE runs separately:
>
> ```sql
> UPDATE members SET active_loan_count = active_loan_count + 1
> WHERE id=? AND status='ACTIVE' AND active_loan_count < 5;
> ```
>
> The first sees count=4, increments to 5. The second sees count=5, condition fails, returns 0 rows. We translate to `409 LIMIT_REACHED`. No application-side check window.

---

## Q5. "How are fines computed?"

> Daily idempotent cron runs at midnight:
>
> 1. Find loans where status='BORROWED' AND due_date < today.
> 2. Mark each OVERDUE.
> 3. Accrue late fee = `late_days × rate` (skipping branch closure days). Idempotent via UNIQUE on `(loan_id, accrual_date)`.
> 4. Update `member.outstanding_fines`.
>
> Different fines (LATE, LOST, DAMAGED) use different `FineCalculator` strategies. Composing them gives total fine.

---

## Q6. "How do you handle a member returning a book at a different branch?"

> The return endpoint takes a `branch_id`. If it differs from the loan's `issued_at_branch_id`:
>
> 1. Loan transitions to RETURNED.
> 2. Copy transitions to `IN_TRANSIT` (with branch_id updated to the receiving branch).
> 3. A `TransferTask` is created.
> 4. Librarian marks the copy AVAILABLE once physically shelved.
>
> The member is unblocked immediately; the physical move is async.

---

## Q7. "Can a member renew a book if someone else has reserved it?"

> No. Before allowing renewal, we check:
>
> ```sql
> SELECT 1 FROM reservations WHERE book_id=? AND status='QUEUED' LIMIT 1;
> ```
>
> If found, return `409 RESERVATION_EXISTS`. Otherwise renew is allowed (subject to renewal limit, default 1).

---

## Q8. "How do you scale this?"

> Path:
>
> 1. **Vertical** Postgres for V1.
> 2. **Read replicas** for admin reports.
> 3. **Elasticsearch** for fuzzy search if `pg_trgm` becomes slow.
> 4. **Per-branch sharding** when growing to nationwide chain.
> 5. **Per-region replicas** for multi-country.
>
> Library systems are not big-data; concurrency primitives matter more than throughput.

---

## Q9. "What about lost copies?"

> Member reports lost via API or librarian flags it.
> 1. Loan transitions BORROWED/OVERDUE → LOST.
> 2. Copy transitions BORROWED → LOST.
> 3. `LostBookCalculator` charges 1.5× book replacement cost.
> 4. Member's outstanding fines increases.
>
> If the copy is later recovered (rare), librarian path can transition LOST → AVAILABLE and partially refund.

---

## Q10. "Walk me through extending to e-books."

> E-books use a license model:
>
> - `EbookLicense` aggregate: `concurrent_users`, `max_concurrent`.
> - Borrow decrements `concurrent_users`; returns auto at expiry.
> - No physical copy, no transfer.
>
> The Loan aggregate is reused; only the "inventory" primitive differs. The interface (`borrow returns Loan`) stays the same — Strategy + Adapter let us reuse 80% of code.

---

## Q11. "How do you prevent two reservation workers from promoting the same reservation?"

> `SELECT FOR UPDATE SKIP LOCKED` on the head of the queue. The first worker locks; the second skips and gets the next book's queue. Within a single book, only one worker promotes at a time.
>
> Inside that transaction, we CAS the held copy from AVAILABLE → RESERVED_HOLD. If the copy got snatched (rare race), the CAS fails, the transaction rolls back, and the next event re-triggers promotion.

---

## Q12. "What kind of fraud / abuse should you watch for?"

> - **Sharing accounts** to bypass borrow limit. Detected by IP / device fingerprinting.
> - **Damage avoidance** — member returns at different branch knowing the original branch is stricter.
> - **Reservation abuse** — placing many reservations to "lock" books. We rate-limit reservations per member.
> - **Lost-book scams** — repeatedly claiming lost. Member is flagged after N occurrences.

---

## Q13. "How do you handle holiday closures for fine computation?"

> Each Branch has a `closed_dates` table. The `LateFeeCalculator` counts only **open days** between due_date and today:
>
> ```java
> long openDaysLate = countDaysExcluding(loan.dueDate(), today, branch.closedDates());
> ```
>
> Fine = openDaysLate × perDayRate.

---

## Q14. "What's your idempotency strategy?"

> - `Idempotency-Key` header on borrow / return / pay-fine.
> - UNIQUE constraints on (loan_id, accrual_date), payment idempotency_key, etc.
> - At-most-once processing of events via consumer dedup tables.

---

## Q15. "Where does this design hit limitations first?"

> Three places:
> 1. **Reservation queue at extreme contention** — say, an exam season where 10,000 students reserve the same textbook. Promotion is per-book; no fundamental issue, but notifications spike.
> 2. **Search fuzzy matching** at very large scale (50M+ titles). pg_trgm degrades; we'd add ES.
> 3. **Cross-system inter-library loans** — current model assumes a single org's branches. Multi-org needs a federation layer.

---

Practice each. The library system showcases concurrency primitives at modest scale — exactly what staff candidates demonstrate.
