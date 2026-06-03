# 05 · Vending Machine — Database Design

V1 stores state in process memory + a local audit log (SQLite or JSONL file). V2 (fleet) syncs to a cloud backend.

## Local persistence (SQLite per machine)

```sql
CREATE TABLE inventory (
    slot_code   TEXT PRIMARY KEY,
    product_id  TEXT NOT NULL,
    name        TEXT NOT NULL,
    price_minor INTEGER NOT NULL,
    count       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE cash_float (
    denomination_minor INTEGER PRIMARY KEY,   -- e.g., 100, 500, 1000 (paise)
    count              INTEGER NOT NULL DEFAULT 0,
    kind               TEXT NOT NULL          -- COIN | NOTE
);

CREATE TABLE audit_log (
    seq        INTEGER PRIMARY KEY AUTOINCREMENT,
    ts         TIMESTAMP NOT NULL,
    txn_id     TEXT,                          -- nullable for non-txn events
    kind       TEXT NOT NULL,                 -- PRODUCT_SELECTED, COIN_INSERTED, ...
    payload    TEXT NOT NULL                  -- JSON
);

CREATE INDEX idx_audit_txn ON audit_log (txn_id);
```

The audit log is the **truth**. State machine is reconstructable by replaying events from the last known checkpoint.

## Why SQLite, not flat files?

- Crash-safe writes (WAL mode).
- ACID transactions for the commit step.
- Tiny footprint, embedded.
- Easy queries for operator UI.

## Commit transaction

```sql
BEGIN;
  -- 1. dispense
  UPDATE inventory SET count = count - 1 WHERE slot_code = ? AND count > 0;
  -- ensure 1 row affected; else ROLLBACK
  -- 2. consume escrow → cash_float (add inserted denominations)
  -- 3. emit change → cash_float (subtract change denominations)
  -- 4. log
  INSERT INTO audit_log (ts, txn_id, kind, payload) VALUES (...), (...), (...);
COMMIT;
```

If the hardware dispense fails between BEGIN and COMMIT, we ROLLBACK and re-escrow.

If hardware succeeds but COMMIT fails (crash), recovery on reboot:
- Audit log has the partial events.
- Inventory may be inconsistent. Operator alert; reconcile manually.

## Fleet sync

```sql
CREATE TABLE fleet_sync_state (
    last_synced_seq INTEGER NOT NULL DEFAULT 0
);
```

Background task: read `audit_log WHERE seq > last_synced_seq`, batch upload to cloud, update `last_synced_seq`. Idempotent on cloud side keyed by `(machine_id, seq)`.

## V2 — Fleet backend schema

```sql
CREATE TABLE machines (
    id          UUID PRIMARY KEY,
    serial      TEXT UNIQUE NOT NULL,
    location    TEXT,
    status      TEXT NOT NULL,         -- ONLINE / OFFLINE / MAINTENANCE
    last_seen   TIMESTAMPTZ,
    fw_version  TEXT
);

CREATE TABLE machine_audit (
    machine_id  UUID,
    seq         BIGINT,
    ts          TIMESTAMPTZ NOT NULL,
    kind        TEXT NOT NULL,
    payload     JSONB NOT NULL,
    PRIMARY KEY (machine_id, seq)
) PARTITION BY RANGE (ts);

CREATE INDEX idx_machine_audit_ts ON machine_audit (ts);
```

Partitioned monthly for the same reasons as the streak system: archival, vacuum, quick recent queries.

## Output

```
V1:    in-memory + SQLite local; audit log is the truth
Commit: SQLite transaction over inventory + cash + audit
V2:    fleet backend keyed by (machine_id, seq), partitioned monthly
```
