# 05 · Ride Booking — Database Design

## Storage choices

| Data | Store |
| --- | --- |
| Rides, drivers, payments | Postgres |
| Driver live location | Redis Geo |
| Surge factors | Redis hash + Postgres history |
| Trip path archive | S3 (compressed JSON) + Postgres pointer |
| Driver location archive | Cassandra `(driver_id, ts)` |
| Search (rare) | Postgres |
| Audit log | Postgres + Kafka for replay |

---

## Schemas

### Drivers

```sql
CREATE TABLE drivers (
  id            UUID PRIMARY KEY,
  name          VARCHAR(120) NOT NULL,
  phone         VARCHAR(20) NOT NULL UNIQUE,
  status        VARCHAR(20) NOT NULL,    -- OFFLINE/IDLE/OFFER_PENDING/EN_ROUTE_PICKUP/AT_PICKUP/IN_TRIP
  rating        NUMERIC(3,2) DEFAULT 0,
  total_rides   INT NOT NULL DEFAULT 0,
  city          VARCHAR(80) NOT NULL,
  vehicle_id    UUID NOT NULL,
  last_ping_at  TIMESTAMPTZ,
  version       BIGINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_drivers_city_status ON drivers (city, status);
CREATE INDEX idx_drivers_active ON drivers (city) WHERE status IN ('IDLE','OFFER_PENDING');
```

### Vehicles

```sql
CREATE TABLE vehicles (
  id          UUID PRIMARY KEY,
  driver_id   UUID NOT NULL REFERENCES drivers(id),
  plate       VARCHAR(20) NOT NULL UNIQUE,
  make        VARCHAR(40),
  model       VARCHAR(40),
  color       VARCHAR(30),
  type        VARCHAR(20) NOT NULL,    -- STANDARD/XL/POOL
  active      BOOLEAN NOT NULL DEFAULT TRUE
);
```

### Rides

```sql
CREATE TABLE rides (
  id                 UUID PRIMARY KEY,
  rider_id           UUID NOT NULL,
  driver_id          UUID,
  type               VARCHAR(20) NOT NULL,
  status             VARCHAR(20) NOT NULL,
  pickup_lat         DOUBLE PRECISION NOT NULL,
  pickup_lng         DOUBLE PRECISION NOT NULL,
  pickup_address     VARCHAR(300),
  drop_lat           DOUBLE PRECISION NOT NULL,
  drop_lng           DOUBLE PRECISION NOT NULL,
  drop_address       VARCHAR(300),
  fare_estimate      JSONB NOT NULL,
  fare_final         JSONB,
  surge_factor       NUMERIC(4,2) NOT NULL DEFAULT 1.0,
  trip_distance_km   NUMERIC(8,3),
  trip_duration_min  INT,
  trip_path_url      VARCHAR(300),     -- S3 pointer
  payment_id         UUID,
  cancellation_fee   NUMERIC(10,2),
  version            BIGINT NOT NULL DEFAULT 0,
  idempotency_key    VARCHAR(80) UNIQUE,
  requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  matched_at         TIMESTAMPTZ,
  arrived_at         TIMESTAMPTZ,
  started_at         TIMESTAMPTZ,
  ended_at           TIMESTAMPTZ,
  CONSTRAINT chk_status CHECK (status IN
    ('REQUESTED','MATCHED','ARRIVING','ARRIVED','IN_TRIP','COMPLETED','CANCELLED','NO_SHOW'))
) PARTITION BY RANGE (requested_at);

-- monthly partitions
CREATE TABLE rides_2025_05 PARTITION OF rides FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');

-- read patterns
CREATE INDEX idx_rides_rider ON rides (rider_id, requested_at DESC);
CREATE INDEX idx_rides_driver_active ON rides (driver_id) WHERE status IN ('MATCHED','ARRIVING','ARRIVED','IN_TRIP');
CREATE INDEX idx_rides_pending ON rides (requested_at) WHERE status = 'REQUESTED';
```

### Ride events (audit)

```sql
CREATE TABLE ride_events (
  id          BIGSERIAL,
  ride_id     UUID NOT NULL,
  from_status VARCHAR(20),
  to_status   VARCHAR(20) NOT NULL,
  actor_id    UUID,
  reason      TEXT,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (occurred_at, id)
) PARTITION BY RANGE (occurred_at);
```

### Payments

```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  ride_id         UUID NOT NULL REFERENCES rides(id),
  amount          NUMERIC(10,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          VARCHAR(20) NOT NULL,    -- AUTHORIZED, CAPTURED, FAILED, REFUNDED
  gateway_ref     VARCHAR(120),
  authorized_at   TIMESTAMPTZ,
  captured_at     TIMESTAMPTZ,
  idempotency_key VARCHAR(80) UNIQUE,
  created_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_payments_ride ON payments (ride_id);
```

### Surge zones

```sql
CREATE TABLE surge_history (
  zone_geohash CHAR(7) NOT NULL,
  ride_type    VARCHAR(20) NOT NULL,
  factor       NUMERIC(4,2) NOT NULL,
  computed_at  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (zone_geohash, ride_type, computed_at)
) PARTITION BY RANGE (computed_at);
```

The **live** surge factor lives in Redis (`surge:zone:<geohash7>:<type>`); this table archives history for analytics and audit.

### Idempotency, outbox

Same patterns as in Food Delivery — see `01_Food_Delivery_System/05_database_design.md`.

---

## Locking strategy

| Concern | Strategy |
| --- | --- |
| Ride state transition | Optimistic via `version` |
| Driver state transition | Optimistic via `version` |
| Driver match assignment | `UPDATE WHERE status='IDLE' AND version=?` |
| Payment authorize | UNIQUE on idempotency_key |

The match engine never holds a lock across the offer-window (15 s). It does:

```sql
UPDATE drivers SET status='OFFER_PENDING', version=version+1
WHERE id=? AND status='IDLE' AND version=?
```

If 0 rows updated → driver already taken → try next candidate.

---

## Geospatial indexing — implementation

For drivers, we use **Redis Geo** (`GEOADD driver:locations:<city> <lng> <lat> <driver_id>`).

Match query:

```
GEOSEARCH driver:locations:BLR FROMLONLAT 77.59 12.97 BYRADIUS 3 km ASC COUNT 20
```

Returns up to 20 nearest driver IDs in milliseconds. We then filter by:
- driver type matches ride type (separate set: `drivers:type:STANDARD:BLR`)
- driver status = IDLE (read from Postgres or maintained in a separate Redis hash)

For rare cases where Redis is down, fall back to Postgres with PostGIS:

```sql
SELECT id FROM drivers
WHERE city='BLR' AND status='IDLE'
  AND ST_DWithin(location_geog, ST_MakePoint(?,?)::geography, 3000)
ORDER BY location_geog <-> ST_MakePoint(?,?)::geography
LIMIT 20;
```

PostGIS is slower (~10-30 ms vs <2 ms) but workable.

---

## Partitioning plan

- **rides**: monthly partitions; archive after 1 yr to S3.
- **ride_events**: monthly partitions; drop after 6 mo.
- **payments**: monthly partitions; legal-hold for 7 yrs.
- **drivers / vehicles**: single table; no partitioning (small).

We avoid sharding for V1. When write rate > 10 K/sec consistently, shard by `rider_id`.

---

## Why payment is a separate table

Payments have:
- Their own SLA (retry policy, reconciliation).
- Independent state machine (AUTHORIZED → CAPTURED → REFUNDED).
- Different retention rules (legal: 7 yrs).

Mixing `payment_status` directly into `rides` would create a correctness mess with the gateway webhook.

---

## Quick smoke queries

```sql
-- Active rides per city
SELECT r.id FROM rides r
JOIN drivers d ON d.id = r.driver_id
WHERE r.status IN ('MATCHED','ARRIVING','IN_TRIP') AND d.city = 'BLR';

-- Pending requests waiting for match
SELECT count(*) FROM rides WHERE status='REQUESTED'
  AND requested_at < now() - interval '1 min';

-- Average match time last hour
SELECT avg(matched_at - requested_at) FROM rides
WHERE status != 'CANCELLED'
  AND requested_at > now() - interval '1 hour';
```
