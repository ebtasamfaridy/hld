# 05 · Car Rental — Database Design

## Storage layout

| Domain | Store | Why |
| --- | --- | --- |
| Catalog (VehicleModel, Vehicle) | Postgres + read replicas | Source of truth |
| Vehicle locations (current) | Redis GEO | < 5 ms geosearch |
| Search index (availability summary) | Elasticsearch | Geo + time-window faceted query |
| Time-slot inventory | Postgres (sharded by vehicle_id) | Strong consistency on slot writes |
| Reservation, Trip | Postgres (sharded by user_id, partitioned by month) | Money-bearing |
| Payment | Postgres + Vault for tokens | Auditable |
| GPS breadcrumbs | Postgres (hot, last 7 days) + S3 (cold) | High-volume ingest |
| Damage claims | Postgres | Long-tail workflow |
| Photos | S3 + CDN | Static blobs |
| Outbox / events | Postgres → Kafka via CDC | Reliable publish |

---

## Schemas (Postgres)

### Catalog

```sql
CREATE TABLE vehicle_models (
  id              UUID PRIMARY KEY,
  name            TEXT NOT NULL,
  seats           INT NOT NULL,
  fuel_tank_l     INT NOT NULL,
  hourly_rate_minor BIGINT NOT NULL,
  per_km_rate_minor BIGINT NOT NULL,
  currency        TEXT NOT NULL DEFAULT 'INR',
  active          BOOL NOT NULL DEFAULT true
);

CREATE TABLE vehicles (
  id              UUID PRIMARY KEY,
  model_id        UUID NOT NULL REFERENCES vehicle_models(id),
  plate           TEXT UNIQUE NOT NULL,
  vin             TEXT UNIQUE NOT NULL,
  city_id         UUID NOT NULL,
  status          TEXT NOT NULL,        -- ACTIVE | MAINTENANCE | OUT_OF_SERVICE | RETIRED
  current_lat     DOUBLE PRECISION,
  current_lng     DOUBLE PRECISION,
  last_fuel_pct   INT NOT NULL DEFAULT 100,
  last_odo_km     INT NOT NULL DEFAULT 0,
  last_seen_at    TIMESTAMPTZ
);
CREATE INDEX idx_vehicles_city_status ON vehicles(city_id, status) WHERE status='ACTIVE';
CREATE INDEX idx_vehicles_model       ON vehicles(model_id);
```

### Time-slot inventory (the central table)

```sql
CREATE TABLE timeslots (
  vehicle_id      UUID NOT NULL,
  hour_bucket     BIGINT NOT NULL,        -- epoch hours (UTC)
  reservation_id  UUID NOT NULL,          -- the reservation holding this slot
  version         BIGINT NOT NULL DEFAULT 1,
  created_at      TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (vehicle_id, hour_bucket)
);
CREATE INDEX idx_ts_reservation ON timeslots(reservation_id);
```

> **Design choice**: only **reserved** slots have rows. Free slots are absent. This means:
> - Reservation INSERTs N rows in one TXN; conflicts mean someone has it.
> - Cancellation DELETEs N rows in one TXN.
> - "Is this car free for window W?" → check if any rows exist in `(vehicle_id, [W.start..W.end))`.

### Reservation

```sql
CREATE TABLE reservations (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL,
  vehicle_id      UUID NOT NULL,
  start_at        TIMESTAMPTZ NOT NULL,
  end_at          TIMESTAMPTZ NOT NULL,
  status          TEXT NOT NULL,           -- HELD | CONFIRMED | ACTIVE | COMPLETED | CANCELLED | NO_SHOW | EXPIRED
  base_fare_minor BIGINT NOT NULL,
  deposit_minor   BIGINT NOT NULL,
  currency        TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at      TIMESTAMPTZ NOT NULL,    -- TTL while HELD
  CHECK (end_at > start_at),
  UNIQUE (user_id, idempotency_key)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_resv_user_recent ON reservations(user_id, created_at DESC);
CREATE INDEX idx_resv_vehicle     ON reservations(vehicle_id);
CREATE INDEX idx_resv_status_expire ON reservations(status, expires_at) WHERE status='HELD';
```

### Trip

```sql
CREATE TABLE trips (
  id              UUID PRIMARY KEY,
  reservation_id  UUID NOT NULL UNIQUE REFERENCES reservations(id),
  picked_up_at    TIMESTAMPTZ NOT NULL,
  returned_at     TIMESTAMPTZ,
  odo_start_km    INT NOT NULL,
  odo_end_km      INT,
  fuel_start_pct  INT NOT NULL,
  fuel_end_pct    INT,
  status          TEXT NOT NULL,           -- PICKED_UP | IN_USE | RETURNED | DISPUTED
  final_fare_minor BIGINT,
  pickup_lat      DOUBLE PRECISION,
  pickup_lng      DOUBLE PRECISION,
  return_lat      DOUBLE PRECISION,
  return_lng      DOUBLE PRECISION
);
CREATE INDEX idx_trips_returned ON trips(returned_at) WHERE status='RETURNED';

CREATE TABLE trip_photos (
  trip_id         UUID NOT NULL REFERENCES trips(id),
  phase           TEXT NOT NULL,           -- PRE | POST
  s3_key          TEXT NOT NULL,
  uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (trip_id, phase, s3_key)
);

CREATE TABLE trip_gps (
  trip_id         UUID NOT NULL,
  ts              TIMESTAMPTZ NOT NULL,
  lat             DOUBLE PRECISION,
  lng             DOUBLE PRECISION,
  speed_kph       INT,
  PRIMARY KEY (trip_id, ts)
) PARTITION BY RANGE (ts);
```

### Payment

```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  reservation_id  UUID NOT NULL REFERENCES reservations(id),
  amount_minor    BIGINT NOT NULL,
  status          TEXT NOT NULL,
  gateway         TEXT NOT NULL,
  auth_id         TEXT,
  capture_id      TEXT,
  idempotency_key TEXT NOT NULL,
  UNIQUE (gateway, idempotency_key)
);

CREATE TABLE damage_charges (
  id              UUID PRIMARY KEY,
  claim_id        UUID NOT NULL,
  user_id         UUID NOT NULL,
  amount_minor    BIGINT NOT NULL,
  gateway_ref     TEXT,
  status          TEXT NOT NULL,           -- INITIATED | CHARGED | DECLINED | DUNNING
  idempotency_key TEXT NOT NULL,
  UNIQUE (idempotency_key)
);
```

### Damage claim

```sql
CREATE TABLE damage_claims (
  id              UUID PRIMARY KEY,
  trip_id         UUID NOT NULL REFERENCES trips(id),
  reported_by     UUID NOT NULL,
  severity        TEXT NOT NULL,
  estimate_minor  BIGINT,
  status          TEXT NOT NULL,           -- REPORTED | UNDER_REVIEW | APPROVED | REJECTED | DISPUTED
  reviewer_id     UUID,
  decided_at      TIMESTAMPTZ,
  reason          TEXT
);

CREATE TABLE claim_photos (
  claim_id        UUID NOT NULL REFERENCES damage_claims(id),
  s3_key          TEXT NOT NULL,
  PRIMARY KEY (claim_id, s3_key)
);
```

### Outbox

```sql
CREATE TABLE outbox (
  id              BIGSERIAL PRIMARY KEY,
  aggregate_id    UUID NOT NULL,
  event_type      TEXT NOT NULL,
  payload         JSONB NOT NULL,
  created_at      TIMESTAMPTZ DEFAULT now(),
  published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpub ON outbox(id) WHERE published_at IS NULL;
```

---

## The two queries that matter most

### 1. Atomic reservation across N hour-slots

```sql
-- Wrap in a transaction. We INSERT each slot row; conflict means someone has it.
BEGIN;

-- This INSERT either claims all N slots or none, when paired with a savepoint
-- per slot and rollback on first conflict. Simpler: use a single multi-row INSERT.
INSERT INTO timeslots (vehicle_id, hour_bucket, reservation_id)
VALUES
  ($vehicle_id, $h0, $resv_id),
  ($vehicle_id, $h1, $resv_id),
  ...
  ($vehicle_id, $hN, $resv_id)
ON CONFLICT DO NOTHING;

-- Verify all rows inserted (count = N expected)
SELECT count(*) FROM timeslots
WHERE reservation_id = $resv_id;

-- If count < N, abort and rollback. Else proceed.
INSERT INTO reservations (...) VALUES (...);
INSERT INTO outbox(event_type, payload) VALUES ('ReservationCreated', $...);

COMMIT;
```

The PK `(vehicle_id, hour_bucket)` is the natural mutex. `ON CONFLICT DO NOTHING` is idempotent: re-running with the same `reservation_id` is safe. The post-insert verification catches partial conflicts.

**Alternative**: insert without `ON CONFLICT`, catch unique-violation, rollback. Slightly cleaner error paths but loses idempotency on retry.

### 2. Search "free vehicles in city for window W"

```sql
-- Vehicles with NO conflicting slot rows in [start..end)
SELECT v.id, v.model_id, v.current_lat, v.current_lng
FROM vehicles v
WHERE v.city_id = $city
  AND v.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM timeslots t
    WHERE t.vehicle_id = v.id
      AND t.hour_bucket >= $h_start
      AND t.hour_bucket <  $h_end
  )
ORDER BY <distance from $lat,$lng>
LIMIT 50;
```

This is too slow for 5K QPS at scale. Real implementation:
1. **Pre-compute availability summary** in Elasticsearch: `(vehicle_id, free_until_h, blocked_h_intervals[...])`. Refresh via CDC every 30 s.
2. Search hits ES, filters by city + window + geo, returns vehicle IDs.
3. Optionally re-validate against Postgres at place-reservation time (already done by the atomic INSERT).

Fallback for stale ES: place-reservation will simply fail with OUT_OF_STOCK — user retries.

### 3. Slot release on cancellation

```sql
BEGIN;
UPDATE reservations SET status='CANCELLED', updated_at=now()
 WHERE id = $resv_id AND status IN ('HELD','CONFIRMED');

DELETE FROM timeslots WHERE reservation_id = $resv_id;

INSERT INTO outbox(event_type, payload) VALUES ('ReservationCancelled', $...);
COMMIT;
```

### 4. TTL sweep for HELD reservations

```sql
-- Cron every 30 s
UPDATE reservations SET status='EXPIRED'
 WHERE status='HELD' AND expires_at < now()
RETURNING id;

-- For each expired id, DELETE its timeslots (separate TXN)
```

---

## Indexing summary

| Table | Index | Purpose |
| --- | --- | --- |
| vehicles | `(city_id, status)` partial | search filter |
| timeslots | PK `(vehicle_id, hour_bucket)` | atomic mutex |
| timeslots | `(reservation_id)` | release on cancel |
| reservations | UNIQUE `(user_id, idempotency_key)` | idempotency |
| reservations | `(user_id, created_at DESC)` | "my reservations" |
| reservations | partial `(status, expires_at) WHERE status='HELD'` | TTL sweep |
| trips | UNIQUE `(reservation_id)` | 1:1 |
| trip_gps | range partition on `ts` (daily) | retention |
| payments | UNIQUE `(gateway, idempotency_key)` | gateway dedup |

---

## Caching

| Cache | Key | TTL | Invalidation |
| --- | --- | --- | --- |
| Vehicle detail | `veh:vehicleId` | 5 min | On VehicleUpdated event |
| Search results | `srch:city:{lat}:{lng}:{w}` | 30 s | TTL only |
| Vehicle current location | Redis GEO `vehicles_geo` | live | Updated by IoT pings |
| Reservation snapshot | `resv:reservationId` | 1 hr | On status change |

---

## Output

```
Catalog:        Postgres (active vehicles per city)
Inventory:      timeslots row per (vehicle, hour) — only when reserved
Reservation:    Postgres, sharded by user, partitioned monthly
Trip:           Postgres + GPS in cold tier
Payment:        Postgres + Vault tokens; idempotency-keyed
Damage:         async claims + MIT charge ledger
Hot path query: search (ES) + atomic INSERT timeslots (PG)
Idempotency:    UNIQUE constraints everywhere money is moved
```
