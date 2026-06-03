# 05 · Elevator System — Database Design

V1 is in-memory. Persistence applies to **audit logs** locally and **fleet state** in V2.

## Local audit (per building)

```sql
CREATE TABLE audit_log (
    seq         INTEGER PRIMARY KEY AUTOINCREMENT,
    ts          TIMESTAMP NOT NULL,
    car_id      INTEGER,        -- NULL for building-level events
    kind        TEXT NOT NULL,
    payload     TEXT NOT NULL    -- JSON
);

CREATE INDEX idx_audit_car_ts ON audit_log (car_id, ts);
CREATE INDEX idx_audit_kind   ON audit_log (kind);
```

Used for: post-hoc analysis (avg wait time per floor), legal traceability (who was in the car at the time of an incident), maintenance schedules (door-cycle counts).

## Fleet (V2) — building registry

```sql
CREATE TABLE buildings (
    id          UUID PRIMARY KEY,
    name        TEXT,
    address     TEXT,
    floors      INT,
    car_count   INT,
    fw_version  TEXT,
    last_seen   TIMESTAMPTZ
);

CREATE TABLE cars (
    id          UUID PRIMARY KEY,
    building_id UUID REFERENCES buildings(id),
    car_no      INT,
    capacity_persons INT,
    capacity_kg INT,
    status      TEXT,
    last_floor  INT,
    last_direction TEXT,
    last_seen   TIMESTAMPTZ,
    UNIQUE (building_id, car_no)
);

CREATE TABLE car_audit (
    building_id UUID,
    car_id      UUID,
    seq         BIGINT,
    ts          TIMESTAMPTZ,
    kind        TEXT,
    payload     JSONB,
    PRIMARY KEY (building_id, car_id, seq)
) PARTITION BY RANGE (ts);
```

Heartbeats are time-series → consider InfluxDB / Prometheus instead of Postgres.

## Aggregates for analytics

```sql
-- avg wait per floor per day
CREATE MATERIALIZED VIEW wait_time_daily AS
SELECT building_id,
       date_trunc('day', ts) AS day,
       (payload->>'floor')::int AS floor,
       AVG((payload->>'wait_seconds')::numeric) AS avg_wait
FROM car_audit
WHERE kind = 'HALL_CALL_SERVED'
GROUP BY 1, 2, 3;
```

Refreshed nightly.

## Output

```
V1:    in-memory state + local SQLite audit
V2:    building registry, car registry, partitioned audit, time-series for heartbeats
```
