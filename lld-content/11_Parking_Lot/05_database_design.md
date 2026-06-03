# 05 · Parking Lot — Database Design

## Postgres schema

```sql
CREATE TABLE lots (
    id UUID PRIMARY KEY,
    name TEXT,
    address TEXT,
    floors INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE spots (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL REFERENCES lots(id),
    floor INT NOT NULL,
    row_no INT NOT NULL,
    col_no INT NOT NULL,
    spot_type TEXT NOT NULL,                  -- BIKE | COMPACT | LARGE | EV | HANDICAP
    occupied BOOLEAN NOT NULL DEFAULT FALSE,
    current_ticket UUID NULL,                 -- FK to tickets(id)
    UNIQUE (lot_id, floor, row_no, col_no)
);

CREATE INDEX idx_spots_avail ON spots (lot_id, spot_type, occupied) WHERE occupied = FALSE;

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL,
    plate TEXT NOT NULL,
    vehicle_type TEXT NOT NULL,
    spot_id UUID NOT NULL REFERENCES spots(id),
    entered_at TIMESTAMPTZ NOT NULL,
    exited_at TIMESTAMPTZ NULL,
    fee_minor BIGINT NULL,
    payment_ref TEXT NULL,
    status TEXT NOT NULL,                     -- ACTIVE | PAID | CLOSED
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tickets_plate_active ON tickets (plate) WHERE status = 'ACTIVE';
CREATE INDEX idx_tickets_lot_status ON tickets (lot_id, status);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    lot_id UUID NOT NULL,
    plate TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    held_spot UUID NULL,
    status TEXT NOT NULL,                     -- HELD | CONFIRMED | CANCELLED | EXPIRED
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_res_active ON reservations (lot_id, starts_at, ends_at) WHERE status IN ('HELD', 'CONFIRMED');

CREATE TABLE audit_log (
    seq BIGSERIAL PRIMARY KEY,
    lot_id UUID NOT NULL,
    ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind TEXT NOT NULL,
    payload JSONB NOT NULL
) PARTITION BY RANGE (ts);
```

## The atomic-claim query

```sql
UPDATE spots
SET occupied = TRUE, current_ticket = $1
WHERE id = $2 AND occupied = FALSE
RETURNING id;
```

Postgres `UPDATE` is row-locked → exactly one of N concurrent claims succeeds; others get 0 rows returned.

We **prefer** this over `SELECT FOR UPDATE` because we don't hold a transaction open; the conflict resolution happens in one round-trip.

## Free-spot lookup hot path

Two viable shapes:

**Option A — partial index per type:**
```sql
CREATE INDEX idx_spots_avail ON spots (lot_id, spot_type, occupied) WHERE occupied = FALSE;
```
Cheap reads; small index.

**Option B — counters per type:**
A separate `lot_capacity` table maintained via trigger; gives O(1) "is anything free?" but trades index complexity.

Use A unless reads dominate.

## Reservation conflict prevention

For the V2 reservation feature: a single spot can only be `HELD` or `CONFIRMED` for one window at a time.

```sql
-- exclusion-style check via app-layer query during HOLD:
SELECT 1 FROM reservations
WHERE held_spot = $1
  AND status IN ('HELD', 'CONFIRMED')
  AND tstzrange(starts_at, ends_at) && tstzrange($2, $3);
```

If overlap exists, refuse the hold. Idempotency key on the request to make retries safe.

PostgreSQL has `EXCLUDE USING gist` for range-overlap constraints if we add `btree_gist` extension; that's V2.

## Audit partitioning

Monthly partitions on `audit_log.ts`. Same pattern as `06_Streak_System/05_database_design.md`.

## Output

```
Tables:    lots, spots, tickets, reservations, audit_log (partitioned)
Hot ops:   UPDATE … WHERE occupied=FALSE (atomic claim)
Indexes:   partial on free spots; plate-active for lost-ticket lookup
V2:        reservation overlap check; tstzrange with btree_gist
```
