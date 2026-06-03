# 01 · Library — Requirements

## Problem statement

Design a library management system used by a chain of branches. Members search for books, borrow available copies, return them by the due date, and pay fines if late. They can reserve unavailable books and get notified when a copy becomes free. Librarians manage catalog, copies, members, fines.

We design the **backend** (one service, optionally micro-fronted later).

---

## Functional requirements

### Core (in scope)

**Member-facing**
- Sign up / log in.
- Search books (title, author, ISBN, genre).
- See availability per branch.
- Borrow an available copy (subject to limits).
- Reserve a book if no copy is available; get notified when available.
- Return a borrowed copy at any branch (transfer if branch differs).
- View loans, due dates, fines, history.
- Pay a fine.

**Librarian-facing**
- Add / update books, copies, branches.
- Mark a copy lost / damaged / under repair.
- Issue and return loans manually.
- View overdue list, fines, top borrowers.
- Override fines (with reason).
- Transfer copies between branches.

**Platform-facing**
- Enforce member borrow limits (e.g., max 5 simultaneous loans).
- Compute fines daily for overdue loans.
- Send reminders 2 days before due date.
- Promote reservations to loans when copies become available.

### Extensions (acknowledged, not built)

- E-book / audiobook support.
- Inter-library loans (across organizations).
- Recommendations.
- Member tiers (priority reservations).
- Mobile self-checkout via barcode.
- Pre-paid wallet for fines.

### Out of scope

- Mobile apps.
- Identity provider internals.
- Payment processor internals (we integrate via `PaymentGateway`).
- Cataloging metadata sourcing (we trust input).

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Search p99 | < 200 ms | UX |
| Borrow p99 | < 300 ms | Counter throughput |
| Concurrency | strong consistency on copy availability | No double-borrow |
| Availability | 99.9 % | Public service |
| Audit | every loan/return logged | Accountability |
| Scale | 10 M books, 100 K members, 100 branches | Mid-large library system |
| Throughput | ~50 borrows/sec peak | Counter rush hours |

---

## Actors

```
Member       - search, borrow, return, reserve, pay fine
Librarian    - manage catalog, copies, fines, manual ops
Admin        - branches, policies, reports
NotificationService - reminders, reservations available
PaymentGateway      - external; fines
ReportingService    - admin reports
```

---

## Edge cases

| Case | Handling |
| --- | --- |
| Two members want the last copy | First wins via DB CAS; second auto-reserves |
| Member at borrow limit tries to borrow | Reject `409 LIMIT_REACHED` |
| Member with unpaid fines tries to borrow | Reject; require payment first |
| Lost copy returned later | Record as `RECOVERED`; refund lost-book fee partially? per policy |
| Reservation queue, top member doesn't pick up in 24h | Promote next reservation |
| Copy goes under repair while reserved | Cancel reservation or notify queue |
| Member account suspended mid-loan | Loans stay; no new ones |
| Return at different branch than borrow | Allowed; trigger transfer task |
| Damaged copy returned | Damage fine + flag for librarian review |
| Holiday / library closed at due date | Adjust due date by closure days |
| Renewal | Allowed if no reservations exist; one renewal max |

---

## Output

```
Actors:        Member, Librarian, Admin, Notification, Payment, Reporting
Core FR:       search, borrow, return, reserve, fine, pay, manage catalog
NFR:           strong consistency on copy state; 50 borrows/sec; 99.9% avail
Out of Scope:  mobile, payment internals, IDP
Extensions:    e-books, ILL, recs, tiers, self-checkout
```
