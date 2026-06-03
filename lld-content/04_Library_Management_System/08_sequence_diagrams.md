# 08 · Library — Sequence Diagrams

## 1. Borrow — happy path

```mermaid
sequenceDiagram
  autonumber
  participant M as Member App
  participant LS as LoanService
  participant MS as MemberService
  participant CR as BookCopyRepository
  participant LR as LoanRepository
  participant DB as Postgres

  M->>LS: POST /loans (member, book, branch, idemKey)
  LS->>LS: idem lookup
  LS->>MS: canBorrow(memberId)
  MS-->>LS: ok / reason
  LS->>CR: find available copy of book at branch
  CR-->>LS: copy {id, version}
  LS->>DB: BEGIN
  LS->>CR: UPDATE copy SET status='BORROWED' WHERE id=? AND status='AVAILABLE' AND version=?
  alt 1 row updated
    LS->>MS: increment active_loan_count (CAS)
    LS->>LR: insert loan(BORROWED, dueDate)
    LS->>DB: insert outbox(LoanIssued)
    LS->>DB: COMMIT
    LS-->>M: 201 {loan}
  else 0 rows
    LS->>DB: ROLLBACK
    LS->>CR: try another copy at any branch
    Note over LS: Retry up to N times
    LS-->>M: 409 NO_COPY_AVAILABLE
  end
```

---

## 2. Return — same branch

```mermaid
sequenceDiagram
  participant M as Member
  participant LS as LoanService
  participant CR as BookCopyRepository
  participant FS as FineService
  participant K as Kafka

  M->>LS: POST /loans/{id}:return {branch}
  LS->>LS: load loan, verify state
  LS->>CR: UPDATE copy SET status='AVAILABLE' WHERE id=?
  LS->>LS: loan.status -> RETURNED, returnedAt=now
  LS->>FS: finalize fines (if late)
  LS->>K: LoanReturned event
  LS-->>M: 200 OK
```

---

## 3. Return — different branch (transfer)

```mermaid
sequenceDiagram
  participant M as Member
  participant LS as LoanService
  participant CR as BookCopyRepository
  participant TR as TransferService

  M->>LS: POST /loans/{id}:return {branch=br_2}
  LS->>LS: branch differs from origin (br_1)
  LS->>CR: UPDATE copy SET branch_id=br_2, status='IN_TRANSIT' WHERE id=?
  LS->>TR: createTransferTask(copyId, fromBranch, toBranch)
  LS->>LS: loan.status -> RETURNED
  Note right of TR: librarian later marks status='AVAILABLE' after physical handling
  LS-->>M: 200 {transferred:true}
```

The library staff confirms the physical move and flips status to AVAILABLE later.

---

## 4. Reserve — no copies available

```mermaid
sequenceDiagram
  participant M as Member
  participant RS as ReservationService

  M->>RS: POST /reservations
  RS->>RS: check no available copies system-wide
  RS->>RS: insert Reservation(QUEUED, position=tail+1)
  RS-->>M: 201 {position}
```

---

## 5. Promote on return (reservation queue)

```mermaid
sequenceDiagram
  participant LS as LoanService
  participant K as Kafka
  participant RS as ReservationService
  participant N as NotificationService

  LS->>K: LoanReturned (or CopyAvailable)
  K->>RS: consume
  RS->>RS: SELECT head reservation FOR UPDATE SKIP LOCKED
  alt has queued
    RS->>RS: reservation -> READY, ready_at=now, expires_at=+24h
    RS->>BookCopy: status -> RESERVED_HOLD (link to reservation)
    RS->>K: ReservationReady
    K->>N: notify member
  else queue empty
    Note over RS: nothing to do
  end
```

The `RESERVED_HOLD` prevents random borrowing of the held copy. Only the reservation's member can borrow it.

---

## 6. Reservation expires (no pickup)

```mermaid
sequenceDiagram
  participant CRON as Hourly Cron
  participant RS as ReservationService

  CRON->>RS: expireUnclaimed()
  RS->>RS: find reservations with READY + expires_at < now
  loop each
    RS->>RS: status -> EXPIRED
    RS->>BookCopy: status RESERVED_HOLD -> AVAILABLE
    RS->>RS: trigger promoteNextInQueue() for that book
  end
```

A small chain of expirations may unwind, but each expiry promotes one new reservation; the queue stays correct.

---

## 7. Fine accrual — daily

```mermaid
sequenceDiagram
  participant CRON as Daily Cron
  participant LR as LoanRepository
  participant FS as FineService

  CRON->>LR: find loans where status='BORROWED' AND due_date < today
  LR-->>CRON: overdue loans
  loop each
    CRON->>LR: UPDATE status='OVERDUE'
    CRON->>FS: accrueLateFee(loan, today)   -- idempotent on (loan, accrual_date)
    FS->>Member: increment outstandingFineBalance
  end
```

Idempotency: the `fine_accruals` table has `UNIQUE(loan_id, accrual_date)`, so re-running the cron the same day doesn't double-charge.

---

## 8. Pay fine

```mermaid
sequenceDiagram
  participant M as Member
  participant FS as FineService
  participant PG as PaymentGateway

  M->>FS: POST /fines/{id}:pay (idemKey)
  FS->>FS: load fine, verify OUTSTANDING
  FS->>PG: charge(amount, idemKey)
  PG-->>FS: success
  FS->>FS: fine.status=PAID, paid_at=now, paid_amount=amount
  FS->>Member: decrement outstandingFineBalance
  FS-->>M: 200 OK
```

---

## 9. Lost book

```mermaid
sequenceDiagram
  participant M as Member
  participant LS as LoanService
  participant FS as FineService

  M->>LS: POST /loans/{id}:report-lost
  LS->>LS: loan.status -> LOST, copy.status -> LOST
  LS->>FS: applyLostFee(loan)   -- 1.5x replacement
  FS-->>LS: fine created
  LS-->>M: 200 OK {fineId}
```

---

## What these reveal

- Borrow is the most contentious flow; CAS is essential.
- Return triggers a chain (fine accrual finalize, queue promotion, transfer if needed).
- Reservation queue uses `SELECT FOR UPDATE SKIP LOCKED` to avoid races.
- Daily crons must be idempotent (unique constraints).
- Every state transition emits an event for audit and downstream services.
