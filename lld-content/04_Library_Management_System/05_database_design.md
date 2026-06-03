# 05 · Library — Database Design

## Schemas

### Books

```sql
CREATE TABLE books (
  id            UUID PRIMARY KEY,
  isbn          VARCHAR(20) UNIQUE,
  title         VARCHAR(300) NOT NULL,
  authors       TEXT[] NOT NULL,
  genres        TEXT[] NOT NULL,
  published_year INT,
  language      CHAR(2) DEFAULT 'EN',
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_books_isbn ON books (isbn);
CREATE INDEX idx_books_title_trgm ON books USING gin (title gin_trgm_ops);
CREATE INDEX idx_books_authors ON books USING gin (authors);
CREATE INDEX idx_books_genres ON books USING gin (genres);
```

### Branches

```sql
CREATE TABLE branches (
  id      UUID PRIMARY KEY,
  name    VARCHAR(120) NOT NULL,
  address TEXT,
  active  BOOLEAN DEFAULT TRUE
);

CREATE TABLE branch_closed_dates (
  branch_id UUID NOT NULL REFERENCES branches(id),
  closed_on DATE NOT NULL,
  reason    TEXT,
  PRIMARY KEY (branch_id, closed_on)
);
```

### Book copies

```sql
CREATE TABLE book_copies (
  id              UUID PRIMARY KEY,
  book_id         UUID NOT NULL REFERENCES books(id),
  branch_id       UUID NOT NULL REFERENCES branches(id),
  shelf_location  VARCHAR(40),
  status          VARCHAR(20) NOT NULL,
  acquired_at     DATE,
  last_seen_at    TIMESTAMPTZ,
  version         BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_status CHECK (status IN
    ('AVAILABLE','BORROWED','RESERVED_HOLD','IN_REPAIR','LOST','IN_TRANSIT'))
);

CREATE INDEX idx_copies_book_branch ON book_copies (book_id, branch_id);
CREATE INDEX idx_copies_book_avail ON book_copies (book_id) WHERE status = 'AVAILABLE';
CREATE INDEX idx_copies_status ON book_copies (status);
```

The partial index `idx_copies_book_avail` is critical — it makes "find an available copy" fast.

### Members

```sql
CREATE TABLE members (
  id                    UUID PRIMARY KEY,
  name                  VARCHAR(120) NOT NULL,
  email                 VARCHAR(120) NOT NULL UNIQUE,
  status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  active_loan_count     INT NOT NULL DEFAULT 0,
  outstanding_fines     NUMERIC(10,2) NOT NULL DEFAULT 0,
  joined_at             TIMESTAMPTZ DEFAULT now(),
  version               BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);
```

### Loans

```sql
CREATE TABLE loans (
  id                  UUID PRIMARY KEY,
  member_id           UUID NOT NULL REFERENCES members(id),
  copy_id             UUID NOT NULL REFERENCES book_copies(id),
  issued_at_branch_id UUID NOT NULL,
  issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  due_date            DATE NOT NULL,
  returned_at         TIMESTAMPTZ,
  returned_at_branch_id UUID,
  status              VARCHAR(20) NOT NULL,
  renewals            INT NOT NULL DEFAULT 0,
  version             BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_loan_status CHECK (status IN
    ('BORROWED','OVERDUE','RETURNED','LOST','DAMAGED'))
);

CREATE INDEX idx_loans_member_active ON loans (member_id) WHERE status IN ('BORROWED','OVERDUE');
CREATE INDEX idx_loans_copy_active ON loans (copy_id) WHERE status IN ('BORROWED','OVERDUE');
CREATE INDEX idx_loans_overdue ON loans (due_date) WHERE status = 'BORROWED';
CREATE UNIQUE INDEX uniq_loans_one_active_per_copy ON loans (copy_id) WHERE status IN ('BORROWED','OVERDUE');
```

The **unique partial index** `uniq_loans_one_active_per_copy` is a critical safety net — at most one active loan per copy.

### Reservations

```sql
CREATE TABLE reservations (
  id              UUID PRIMARY KEY,
  member_id       UUID NOT NULL REFERENCES members(id),
  book_id         UUID NOT NULL REFERENCES books(id),
  preferred_branch_id UUID,
  status          VARCHAR(20) NOT NULL,
  queue_position  INT,
  ready_at        TIMESTAMPTZ,
  expires_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_res_status CHECK (status IN
    ('QUEUED','READY','EXPIRED','FULFILLED','CANCELLED'))
);

CREATE INDEX idx_res_book_active ON reservations (book_id, queue_position) WHERE status IN ('QUEUED','READY');
CREATE INDEX idx_res_member ON reservations (member_id);
CREATE UNIQUE INDEX uniq_res_member_book ON reservations (member_id, book_id) WHERE status IN ('QUEUED','READY');
```

Member can have only one active reservation per book.

### Fines

```sql
CREATE TABLE fines (
  id          UUID PRIMARY KEY,
  member_id   UUID NOT NULL REFERENCES members(id),
  loan_id     UUID REFERENCES loans(id),
  kind        VARCHAR(20) NOT NULL,         -- LATE / LOST / DAMAGED
  amount      NUMERIC(10,2) NOT NULL,
  paid_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  status      VARCHAR(20) NOT NULL,
  created_at  TIMESTAMPTZ DEFAULT now(),
  paid_at     TIMESTAMPTZ,
  waived_by   UUID,
  waiver_reason TEXT,
  CONSTRAINT chk_fine_status CHECK (status IN ('OUTSTANDING','PAID','WAIVED'))
);

CREATE INDEX idx_fines_member_outstanding ON fines (member_id) WHERE status='OUTSTANDING';
CREATE INDEX idx_fines_loan ON fines (loan_id);
```

### Audit / events

```sql
CREATE TABLE loan_events (
  id          BIGSERIAL,
  loan_id     UUID NOT NULL,
  from_status VARCHAR(20),
  to_status   VARCHAR(20) NOT NULL,
  actor_id    UUID,
  reason      TEXT,
  occurred_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (occurred_at, id)
);
```

### Outbox

```sql
CREATE TABLE outbox_events (
  id           UUID PRIMARY KEY,
  event_type   VARCHAR(50) NOT NULL,
  payload      JSONB NOT NULL,
  created_at   TIMESTAMPTZ DEFAULT now(),
  published_at TIMESTAMPTZ
);
```

---

## Locking strategy

| Concern | Strategy |
| --- | --- |
| Borrow a copy | Atomic UPDATE WHERE status='AVAILABLE' AND version=? |
| Reservation queue head | SELECT FOR UPDATE SKIP LOCKED on top reservation |
| Member loan increment | Atomic UPDATE WHERE active_loan_count < limit |
| Fine accrual | INSERT (idempotent on (loan_id, accrual_date)) |
| Loan return | UPDATE WHERE id=? AND status IN ('BORROWED','OVERDUE') AND version=? |

### Borrow CAS

```sql
UPDATE book_copies
SET status='BORROWED', version=version+1
WHERE id=$copy_id AND status='AVAILABLE' AND version=$expected_version;
```

If 0 rows → another member got it. We pick a different available copy of the same book and retry.

### Member loan increment

```sql
UPDATE members
SET active_loan_count = active_loan_count + 1, version=version+1
WHERE id=$member_id AND active_loan_count < $maxLimit AND status='ACTIVE';
```

Atomic check-and-increment. No application-side race.

---

## Daily fine cron

```sql
-- Mark loans overdue
UPDATE loans SET status='OVERDUE', version=version+1
WHERE status='BORROWED' AND due_date < CURRENT_DATE;

-- Insert one fine per day per overdue loan (idempotent)
INSERT INTO fines (id, member_id, loan_id, kind, amount, status)
SELECT gen_random_uuid(), l.member_id, l.id, 'LATE',
       (CURRENT_DATE - l.due_date) * 5.00, 'OUTSTANDING'
FROM loans l
WHERE l.status='OVERDUE'
ON CONFLICT (loan_id, accrual_date) DO NOTHING;
```

Or accumulate into a single LATE fine and increment its `amount` daily. Either pattern works; pick one and document.

We use a `unique (loan_id, accrual_date)` constraint on a separate `fine_accruals` table to make daily accrual idempotent.

---

## Sample queries

```sql
-- Find available copy of book at preferred branch
SELECT id, version FROM book_copies
WHERE book_id = $book AND branch_id = $branch AND status='AVAILABLE'
LIMIT 1;

-- Reservation queue position
SELECT count(*) FROM reservations
WHERE book_id=$book AND status='QUEUED' AND created_at < (
  SELECT created_at FROM reservations WHERE id=$res
);

-- Member's active loans
SELECT l.id, b.title, l.due_date FROM loans l
JOIN book_copies c ON c.id=l.copy_id
JOIN books b ON b.id=c.book_id
WHERE l.member_id=$member AND l.status IN ('BORROWED','OVERDUE');

-- Overdue list (admin)
SELECT l.id, m.name, b.title, (CURRENT_DATE - l.due_date) AS days_late
FROM loans l
JOIN members m ON m.id = l.member_id
JOIN book_copies c ON c.id = l.copy_id
JOIN books b ON b.id = c.book_id
WHERE l.status='OVERDUE'
ORDER BY days_late DESC;
```

---

## Why Postgres alone

Numbers are modest. Postgres handles 50 borrows/sec on a small instance. Strong consistency comes free. Joins help admin queries.

Elasticsearch is optional for fuzzy search — but `pg_trgm` (with `gin gin_trgm_ops`) covers basic `LIKE '%word%'` very well for 5M titles.
