# 05 · Hotel Booking — Database Design

## Schemas

### Hotel + RoomType

```sql
CREATE TABLE hotels (
  id           UUID PRIMARY KEY,
  name         VARCHAR(200) NOT NULL,
  city         VARCHAR(80) NOT NULL,
  country      CHAR(2) NOT NULL,
  lat          DOUBLE PRECISION NOT NULL,
  lng          DOUBLE PRECISION NOT NULL,
  rating       NUMERIC(3,2) DEFAULT 0,
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_hotels_city ON hotels (city) WHERE active;
CREATE INDEX idx_hotels_geo ON hotels USING gist (ll_to_earth(lat, lng));

CREATE TABLE room_types (
  id            UUID PRIMARY KEY,
  hotel_id      UUID NOT NULL REFERENCES hotels(id),
  name          VARCHAR(80) NOT NULL,
  max_occupancy INT NOT NULL,
  amenities     TEXT[] DEFAULT '{}',
  cancellation_policy_id UUID,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (hotel_id, name)
);
```

### Room inventory ⭐

The most important table.

```sql
CREATE TABLE room_inventory (
  hotel_id        UUID NOT NULL,
  room_type_id    UUID NOT NULL,
  date            DATE NOT NULL,
  total_rooms     INT NOT NULL CHECK (total_rooms >= 0),
  available_rooms INT NOT NULL CHECK (available_rooms >= 0 AND available_rooms <= total_rooms),
  base_price      NUMERIC(10,2) NOT NULL CHECK (base_price >= 0),
  blocked         BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (hotel_id, room_type_id, date)
) PARTITION BY RANGE (date);

-- Monthly partitions, retained for 1 yr forward + 6 mo back
CREATE TABLE room_inventory_2025_05 PARTITION OF room_inventory FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
```

The PK enforces uniqueness of (hotel, room_type, date). CHECK constraints protect invariants.

**Atomic decrement** for booking:

```sql
UPDATE room_inventory
SET    available_rooms = available_rooms - $1, updated_at = now()
WHERE  hotel_id = $2 AND room_type_id = $3 AND date = $4
  AND  blocked = FALSE
  AND  available_rooms >= $1;
```

If `affected_rows == 0`, no inventory. The booking transaction rolls back any prior decrements.

### Bookings

```sql
CREATE TABLE bookings (
  id                 UUID PRIMARY KEY,
  guest_id           UUID NOT NULL,
  hotel_id           UUID NOT NULL,
  room_type_id       UUID NOT NULL,
  check_in           DATE NOT NULL,
  check_out          DATE NOT NULL,
  room_count         INT NOT NULL CHECK (room_count >= 1),
  adult_count        INT NOT NULL,
  child_count        INT NOT NULL DEFAULT 0,
  status             VARCHAR(20) NOT NULL,
  total_price        NUMERIC(10,2) NOT NULL,
  currency           CHAR(3) NOT NULL DEFAULT 'INR',
  price_breakdown    JSONB NOT NULL,            -- per-night snapshot
  cancellation_policy JSONB NOT NULL,            -- snapshot
  payment_id         UUID,
  idempotency_key    VARCHAR(80) UNIQUE,
  version            BIGINT NOT NULL DEFAULT 0,
  created_at         TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT chk_dates CHECK (check_out > check_in),
  CONSTRAINT chk_status CHECK (status IN
    ('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW'))
);

CREATE INDEX idx_bookings_guest ON bookings (guest_id, created_at DESC);
CREATE INDEX idx_bookings_hotel_dates ON bookings (hotel_id, check_in)
  WHERE status IN ('CONFIRMED','CHECKED_IN');
CREATE INDEX idx_bookings_active ON bookings (status) WHERE status IN ('CONFIRMED','CHECKED_IN');
```

### Booking events (audit)

```sql
CREATE TABLE booking_events (
  id          BIGSERIAL,
  booking_id  UUID NOT NULL,
  from_status VARCHAR(20),
  to_status   VARCHAR(20) NOT NULL,
  actor_id    UUID,
  reason      TEXT,
  occurred_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (occurred_at, id)
) PARTITION BY RANGE (occurred_at);
```

### Payments

```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  booking_id      UUID NOT NULL REFERENCES bookings(id),
  amount          NUMERIC(10,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          VARCHAR(20) NOT NULL,
  gateway_ref     VARCHAR(120),
  authorized_at   TIMESTAMPTZ,
  captured_at     TIMESTAMPTZ,
  refunded_at     TIMESTAMPTZ,
  refunded_amount NUMERIC(10,2) DEFAULT 0,
  idempotency_key VARCHAR(80) UNIQUE,
  created_at      TIMESTAMPTZ DEFAULT now()
);
```

### Outbox (for events to Kafka)

Same pattern as Food Delivery. Crucial for atomic "save booking + publish".

---

## Locking strategy

| Concern | Strategy | Why |
| --- | --- | --- |
| Inventory decrement | DB CAS atomic UPDATE WHERE | No locks; high throughput |
| Booking transitions | Optimistic via `version` | Low conflict |
| Hotel inventory bulk update | Pessimistic per range | Bounded; admin path; rare |
| Payment idempotent | UNIQUE on idempotency_key | Race-safe |

### Why not pessimistic on inventory?

Room booking is a **read+modify+write** that must be atomic. We could lock with `SELECT FOR UPDATE`, but a single-statement `UPDATE WHERE available > 0` is faster and equally correct.

The atomic UPDATE acquires a row lock for ~1 ms. Many parallel UPDATEs serialize on the same row but each completes quickly.

---

## Concurrency: 5-night booking transaction

```sql
BEGIN;
-- night 1
UPDATE room_inventory SET available_rooms = available_rooms - $cnt
 WHERE hotel_id=$h AND room_type_id=$r AND date='2025-06-01'
   AND blocked = FALSE AND available_rooms >= $cnt;
-- night 2 (similar)
-- night 3 (similar)
-- ... if any returns 0 rows, ROLLBACK
INSERT INTO bookings ...;
INSERT INTO outbox_events (BookingConfirmed) ...;
COMMIT;
```

If any night fails, the whole transaction rolls back. Postgres handles this naturally with serializable isolation.

For high contention, use `READ COMMITTED` (default) — the atomic UPDATE handles serialization correctly because each UPDATE is its own implicit transaction within the larger one.

---

## Search and read replicas

For search, we feed a **read replica** or **Elasticsearch**:

- ES has hotels with denormalized fields: city, amenities, lat/lng, current min price for next 30 days, rating.
- Updates flow via Debezium CDC on the Postgres WAL.
- Search query: `{city, dateRange, occupancy, filters}` → ES → top hotels → batch availability check via Redis.
- ES is **eventually consistent** (~30s lag). The booking flow re-checks atomically.

---

## Partitioning + archiving

- `room_inventory` partitioned by month. Future 12 months kept hot. Past months dropped or archived.
- `bookings` partitioned by `created_at` month. Hot for 1 yr; cold after.
- `booking_events` partitioned by date; drop after 1 yr.
- `payments` partitioned by created_at; legal-hold for 7 yrs.

---

## Sample queries

```sql
-- Availability for hotel × room × dates
SELECT date, available_rooms, base_price FROM room_inventory
WHERE hotel_id = ? AND room_type_id = ? AND date BETWEEN ? AND ?
ORDER BY date;

-- Active bookings at hotel
SELECT * FROM bookings
WHERE hotel_id = ? AND status IN ('CONFIRMED','CHECKED_IN')
  AND check_in <= ? AND check_out > ?;

-- Stuck bookings (PENDING > 5 min)
SELECT * FROM bookings WHERE status='PENDING' AND created_at < now() - interval '5 min';
```

These each hit indexed paths — sub-50 ms.

---

## Why we chose Postgres

Inventory needs:
- ACID (no oversell).
- Atomic UPDATE WHERE.
- Joins (sometimes).
- Mature ops (backup, replication).

Cassandra would offer higher throughput but lacks ACID for the multi-row decrement. We can reach 5 K bookings/sec on a beefy partitioned Postgres + connection pool. Beyond that, shard by `hotel_id`.
