# 05 · Food Delivery — Database Design

## Storage choices per data type

| Data | Storage | Why |
| --- | --- | --- |
| Orders, payments, drivers, restaurants | Postgres | ACID, joins, mature ops |
| Live driver locations | Redis Geo | sub-ms geo queries |
| Inventory (item-level stock) | Redis + Postgres | high read/write rate, atomic ops |
| Tracking subscriptions | Redis pubsub / Kafka | fan-out |
| Audit / event log | Append-only Postgres + Kafka | replay-friendly |
| Search (cuisine / fuzzy) | Elasticsearch (V2) | text & faceting |
| Static assets (images, menus pdfs) | S3 + CDN | large blobs |
| Cold orders (>180 d) | S3 / Glacier | archival |

We focus on Postgres schema below.

---

## Schemas

### Customer

```sql
CREATE TABLE customers (
  id           UUID PRIMARY KEY,
  name         VARCHAR(120) NOT NULL,
  phone        VARCHAR(20)  NOT NULL UNIQUE,
  email        VARCHAR(120) UNIQUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMPTZ
);

CREATE TABLE customer_addresses (
  id           UUID PRIMARY KEY,
  customer_id  UUID NOT NULL REFERENCES customers(id),
  label        VARCHAR(40),                    -- 'Home', 'Office'
  line1        VARCHAR(200) NOT NULL,
  line2        VARCHAR(200),
  city         VARCHAR(80) NOT NULL,
  pincode      VARCHAR(10),
  lat          DOUBLE PRECISION NOT NULL,
  lng          DOUBLE PRECISION NOT NULL,
  contact      VARCHAR(20),
  is_default   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_addresses_customer ON customer_addresses (customer_id);
CREATE UNIQUE INDEX uniq_default_address_per_customer
  ON customer_addresses (customer_id) WHERE is_default = TRUE;
```

> The partial unique index enforces "at most one default address per customer."

### Restaurant + menu

```sql
CREATE TABLE restaurants (
  id            UUID PRIMARY KEY,
  name          VARCHAR(120) NOT NULL,
  status        VARCHAR(20)  NOT NULL,           -- ACTIVE, CLOSED, BANNED
  cuisine_tags  TEXT[] NOT NULL DEFAULT '{}',
  city          VARCHAR(80) NOT NULL,
  lat           DOUBLE PRECISION NOT NULL,
  lng           DOUBLE PRECISION NOT NULL,
  rating        NUMERIC(3,2) DEFAULT 0,
  prep_minutes  INT NOT NULL DEFAULT 20,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_restaurants_city ON restaurants (city);
CREATE INDEX idx_restaurants_geo ON restaurants USING GIST (
  ll_to_earth(lat, lng)
);
-- For Postgres, use cube + earthdistance, or PostGIS for production.

CREATE TABLE menu_categories (
  id           UUID PRIMARY KEY,
  restaurant_id UUID NOT NULL REFERENCES restaurants(id),
  name         VARCHAR(80) NOT NULL,
  sort_order   INT NOT NULL DEFAULT 0
);

CREATE TABLE menu_items (
  id            UUID PRIMARY KEY,
  restaurant_id UUID NOT NULL REFERENCES restaurants(id),
  category_id   UUID REFERENCES menu_categories(id),
  name          VARCHAR(120) NOT NULL,
  description   TEXT,
  price         NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  veg           BOOLEAN NOT NULL,
  available     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_menu_restaurant_avail
  ON menu_items (restaurant_id, available)
  WHERE available = TRUE;
```

### Inventory (per item, per restaurant)

```sql
CREATE TABLE menu_item_inventory (
  menu_item_id  UUID PRIMARY KEY REFERENCES menu_items(id),
  stock_count   INT NOT NULL CHECK (stock_count >= 0),  -- nullable means unlimited
  updated_at    TIMESTAMPTZ DEFAULT now()
);
```

Atomic decrement:

```sql
UPDATE menu_item_inventory
SET stock_count = stock_count - $1, updated_at = now()
WHERE menu_item_id = $2 AND stock_count >= $1;
-- 0 rows updated → not enough stock
```

For most items (unlimited stock — pizza, biryani), inventory is `NULL` and we skip this check.

### Orders ⭐

```sql
CREATE TABLE orders (
  id                 UUID PRIMARY KEY,
  customer_id        UUID NOT NULL REFERENCES customers(id),
  restaurant_id      UUID NOT NULL REFERENCES restaurants(id),
  status             VARCHAR(20) NOT NULL,
  subtotal           NUMERIC(10,2) NOT NULL,
  tax                NUMERIC(10,2) NOT NULL,
  delivery_fee       NUMERIC(10,2) NOT NULL,
  discount           NUMERIC(10,2) NOT NULL DEFAULT 0,
  surge              NUMERIC(10,2) NOT NULL DEFAULT 0,
  total              NUMERIC(10,2) NOT NULL,
  currency           CHAR(3) NOT NULL DEFAULT 'INR',
  delivery_snapshot  JSONB NOT NULL,
  payment_id         UUID,
  assignment_id      UUID,
  version            BIGINT NOT NULL DEFAULT 0,
  idempotency_key    VARCHAR(80) UNIQUE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_total CHECK (total >= 0),
  CONSTRAINT chk_status_enum CHECK (status IN
    ('PLACED','CONFIRMED','PREPARING','READY_FOR_PICKUP',
     'OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REJECTED'))
);

-- read patterns
CREATE INDEX idx_orders_customer_created
  ON orders (customer_id, created_at DESC);

CREATE INDEX idx_orders_active_by_restaurant
  ON orders (restaurant_id)
  WHERE status IN ('PLACED','CONFIRMED','PREPARING','READY_FOR_PICKUP');

CREATE INDEX idx_orders_active_by_assignment
  ON orders (assignment_id)
  WHERE status = 'OUT_FOR_DELIVERY';

-- partition by month (production)
-- CREATE TABLE orders_2025_01 PARTITION OF orders FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

### Order items

```sql
CREATE TABLE order_items (
  id                  UUID PRIMARY KEY,
  order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  menu_item_id        UUID NOT NULL REFERENCES menu_items(id),
  name_snapshot       VARCHAR(120) NOT NULL,
  quantity            INT NOT NULL CHECK (quantity > 0),
  unit_price_snapshot NUMERIC(10,2) NOT NULL,
  line_total          NUMERIC(10,2) NOT NULL,
  CONSTRAINT chk_line_total
    CHECK (line_total = quantity * unit_price_snapshot)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
```

The `name_snapshot` and `unit_price_snapshot` decouple historical orders from current menu changes.

### Drivers

```sql
CREATE TABLE drivers (
  id           UUID PRIMARY KEY,
  name         VARCHAR(120) NOT NULL,
  phone        VARCHAR(20) NOT NULL UNIQUE,
  status       VARCHAR(20) NOT NULL,           -- OFFLINE, IDLE, OFFER_PENDING, BUSY
  vehicle_type VARCHAR(20),
  vehicle_no   VARCHAR(30),
  rating       NUMERIC(3,2) DEFAULT 0,
  city         VARCHAR(80) NOT NULL,
  version      BIGINT NOT NULL DEFAULT 0,      -- optimistic lock
  last_ping_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_drivers_city_status ON drivers (city, status);
```

Drivers' **live location** lives in Redis (`GEOADD driver:locations:<city>`) — not in this table.

### Delivery assignment

```sql
CREATE TABLE delivery_assignments (
  id            UUID PRIMARY KEY,
  order_id      UUID NOT NULL REFERENCES orders(id),
  driver_id     UUID NOT NULL REFERENCES drivers(id),
  status        VARCHAR(20) NOT NULL,
  offered_at    TIMESTAMPTZ NOT NULL,
  responded_at  TIMESTAMPTZ,
  picked_up_at  TIMESTAMPTZ,
  delivered_at  TIMESTAMPTZ,
  expires_at    TIMESTAMPTZ NOT NULL,
  version       BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_assign_status CHECK (status IN
    ('OFFERED','ACCEPTED','REJECTED','EXPIRED','PICKED_UP','DELIVERED','CANCELLED'))
);

CREATE INDEX idx_assignments_driver_active
  ON delivery_assignments (driver_id)
  WHERE status IN ('OFFERED','ACCEPTED','PICKED_UP');

CREATE INDEX idx_assignments_order
  ON delivery_assignments (order_id);
```

### Payment

```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  order_id        UUID NOT NULL REFERENCES orders(id),
  amount          NUMERIC(10,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          VARCHAR(20) NOT NULL,    -- PENDING, CAPTURED, FAILED, REFUNDED
  gateway_ref     VARCHAR(120),
  idempotency_key VARCHAR(80) UNIQUE,
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_payments_order ON payments (order_id);
```

### Idempotency

```sql
CREATE TABLE idempotency_records (
  key            VARCHAR(80) PRIMARY KEY,
  resource_type  VARCHAR(40) NOT NULL,
  resource_id    UUID NOT NULL,
  status_code    INT,
  response_body  JSONB,
  payload_hash   CHAR(64),
  created_at     TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_idempotency_created ON idempotency_records (created_at);
```

### Outbox

```sql
CREATE TABLE outbox_events (
  id             BIGSERIAL PRIMARY KEY,
  aggregate_type VARCHAR(40) NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     VARCHAR(60) NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ DEFAULT now(),
  published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
```

A poller publishes unpublished events to Kafka and stamps `published_at`.

---

## Locking strategy

| Concern | Strategy | Why |
| --- | --- | --- |
| Order status update | Optimistic (`version`) | Conflicts are rare; cheap |
| Driver assignment | `SELECT FOR UPDATE SKIP LOCKED` on candidates | Worker queue idiom |
| Inventory decrement | DB CAS (`UPDATE WHERE stock_count >= n`) | Atomic, no lock |
| Idempotent insert | UNIQUE constraint | Concurrency-safe by design |

### Concrete: assigning a driver

```sql
-- candidate selection
SELECT id
FROM drivers
WHERE city = ? AND status = 'IDLE'
ORDER BY ST_Distance(point_of_pickup, location) ASC
LIMIT 5
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` lets parallel dispatchers pick **different** drivers without blocking each other. Top driver gets the offer; others remain available.

---

## Why no separate "cart" table?

Carts are pre-order, transient, low-value. We store them in Redis with TTL (24 h), keyed by user id. If lost, the user re-adds items.

Storing carts in Postgres would add ~10× the order write load with no business value.

---

## Partitioning plan (when orders DB > 5 TB)

- Partition `orders` by `RANGE(created_at)` per month.
- Move partitions older than 6 months to a read-only archive.
- Drop / archive partitions older than 5 years to S3 + Glacier.
- Foreign-key pointers from `payments` / `assignments` continue to work on archived partitions if we keep them attached.

We avoid sharding for V1. Sharding by `customer_id` is the plan for V2 if writes exceed 5 K/sec.

---

## Schema migration philosophy

- **Forward-only.** Use Flyway / Liquibase.
- Online migrations for live tables: add column → backfill → swap reads → drop old.
- Avoid `ALTER TABLE` that rewrites the table; use `pg_repack` if needed.
- Always test on a recent prod copy.

---

## Quick verification queries (the "smoke test")

```sql
-- All orders in a week, by status
SELECT status, count(*) FROM orders
WHERE created_at >= now() - interval '7 days' GROUP BY status;

-- Active orders per restaurant (uses partial index)
SELECT restaurant_id, count(*) FROM orders
WHERE status IN ('PLACED','CONFIRMED','PREPARING','READY_FOR_PICKUP')
GROUP BY restaurant_id;

-- Drivers idle in a city
SELECT count(*) FROM drivers WHERE city = 'BLR' AND status = 'IDLE';
```

These should each be < 50 ms with the indexes above.
