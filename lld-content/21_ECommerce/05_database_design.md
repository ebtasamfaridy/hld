# 05 · E-Commerce — Database Design

## Storage layout

| Domain | Store | Why |
| --- | --- | --- |
| Catalog (Product, SKU, ListingOffer) | Postgres + read replicas (sharded by product_id) | Source of truth |
| Inventory (`inventory_units`) | Postgres (sharded by sku_id) | Strong consistency, atomic CAS |
| Search index | Elasticsearch | Free-text + faceted query |
| BuyBox cache | Redis | Sub-ms read on every search/PDP |
| Cart | Redis primary + Postgres fallback | Hot reads, low durability OK; persist on checkout |
| Order, OrderItem, Shipment, Payment | Postgres (sharded by user_id, partitioned by month) | Money-bearing |
| Return | Postgres | Long-tail workflow |
| Photos / invoices | S3 + CDN | Static blobs |
| Outbox / events | Postgres → Kafka via CDC | Reliable publish |
| Payment vault | Vendor-managed (PCI scope) | Tokenization |

---

## Schemas (Postgres)

### Catalog

```sql
CREATE TABLE products (
  id              UUID PRIMARY KEY,
  title           TEXT NOT NULL,
  brand           TEXT,
  category_id     UUID NOT NULL,
  description     TEXT,
  primary_photo   TEXT,
  status          TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_products_category ON products(category_id) WHERE status='ACTIVE';

CREATE TABLE skus (
  id              UUID PRIMARY KEY,
  product_id      UUID NOT NULL REFERENCES products(id),
  variant_attrs   JSONB NOT NULL,
  gtin            TEXT,
  weight_g        INT,
  length_cm       INT,
  width_cm        INT,
  height_cm       INT
);
CREATE INDEX idx_skus_product ON skus(product_id);

CREATE TABLE listing_offers (
  id              UUID PRIMARY KEY,
  seller_id       UUID NOT NULL,
  sku_id          UUID NOT NULL REFERENCES skus(id),
  price_minor     BIGINT NOT NULL,
  currency        TEXT NOT NULL DEFAULT 'INR',
  ships_in_days   INT NOT NULL DEFAULT 2,
  status          TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
  updated_at      TIMESTAMPTZ DEFAULT now(),
  UNIQUE (seller_id, sku_id)
);
CREATE INDEX idx_offers_sku  ON listing_offers(sku_id) WHERE status='ACTIVE';
CREATE INDEX idx_offers_seller ON listing_offers(seller_id);
```

### Inventory (the central table for the buy plane)

```sql
CREATE TABLE inventory_units (
  seller_id       UUID NOT NULL,
  sku_id          UUID NOT NULL,
  available       INT NOT NULL,
  reserved        INT NOT NULL DEFAULT 0,
  version         BIGINT NOT NULL DEFAULT 1,
  updated_at      TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (seller_id, sku_id),
  CHECK (available >= 0 AND reserved >= 0)
);
```

> **Design choice**: one row per (seller, sku). The PK is the natural mutex. `available - reserved` is "really available" but we collapse this into `available` and treat reserved as already removed for read paths. Only the order-confirm path manages the ledger transition between reserved → committed (or back to available on cancel).

### Cart

```sql
CREATE TABLE carts (
  user_id         UUID PRIMARY KEY,
  updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE cart_lines (
  user_id         UUID NOT NULL REFERENCES carts(user_id),
  offer_id        UUID NOT NULL,
  qty             INT NOT NULL CHECK (qty > 0),
  price_at_add_minor BIGINT NOT NULL,
  added_at        TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, offer_id)
);
```

(Redis is the hot path: `HSET cart:{userId} {offerId} {qty,priceAtAdd}`. Postgres is durability fallback.)

### Order, OrderItem, Shipment

```sql
CREATE TABLE orders (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL,
  total_minor     BIGINT NOT NULL,
  currency        TEXT NOT NULL,
  status          TEXT NOT NULL,    -- CONFIRMED | IN_FULFILMENT | COMPLETED | CANCELLED | PARTIALLY_REFUNDED
  address_id      UUID NOT NULL,
  idempotency_key TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, idempotency_key)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_orders_user_recent ON orders(user_id, created_at DESC);

CREATE TABLE order_items (
  id              UUID PRIMARY KEY,
  order_id        UUID NOT NULL REFERENCES orders(id),
  offer_id        UUID NOT NULL,
  seller_id       UUID NOT NULL,
  sku_id          UUID NOT NULL,
  qty             INT NOT NULL,
  unit_price_minor BIGINT NOT NULL,
  shipment_id     UUID,             -- assigned at order creation
  status          TEXT NOT NULL     -- ACTIVE | CANCELLED | RETURNED
);
CREATE INDEX idx_oi_order ON order_items(order_id);
CREATE INDEX idx_oi_shipment ON order_items(shipment_id);

CREATE TABLE shipments (
  id              UUID PRIMARY KEY,
  order_id        UUID NOT NULL REFERENCES orders(id),
  seller_id       UUID NOT NULL,
  amount_minor    BIGINT NOT NULL,
  status          TEXT NOT NULL,    -- CREATED | PACKED | DISPATCHED | OUT_FOR_DELIVERY | DELIVERED | CANCELLED | RETURNED
  awb             TEXT,
  carrier         TEXT,
  packed_at       TIMESTAMPTZ,
  dispatched_at   TIMESTAMPTZ,
  delivered_at    TIMESTAMPTZ,
  capture_id      TEXT,             -- gateway capture id, set at dispatch
  version         BIGINT NOT NULL DEFAULT 1
);
CREATE INDEX idx_ship_order ON shipments(order_id);
CREATE INDEX idx_ship_seller_status ON shipments(seller_id, status);
```

### Payment

```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  order_id        UUID NOT NULL UNIQUE REFERENCES orders(id),
  authorized_minor BIGINT NOT NULL,
  captured_minor  BIGINT NOT NULL DEFAULT 0,
  refunded_minor  BIGINT NOT NULL DEFAULT 0,
  currency        TEXT NOT NULL,
  status          TEXT NOT NULL,    -- AUTHORIZED | PARTIALLY_CAPTURED | CAPTURED | PARTIALLY_REFUNDED | REFUNDED | VOIDED
  gateway         TEXT NOT NULL,
  auth_id         TEXT NOT NULL,
  saved_method_token TEXT,          -- for MIT
  idempotency_key TEXT NOT NULL,
  UNIQUE (gateway, idempotency_key),
  CHECK (captured_minor <= authorized_minor),
  CHECK (refunded_minor <= captured_minor)
);

CREATE TABLE captures (
  id              UUID PRIMARY KEY,
  payment_id      UUID NOT NULL REFERENCES payments(id),
  shipment_id     UUID NOT NULL UNIQUE REFERENCES shipments(id),
  amount_minor    BIGINT NOT NULL,
  gateway_ref     TEXT NOT NULL,
  status          TEXT NOT NULL,    -- INITIATED | CAPTURED | FAILED
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE refunds (
  id              UUID PRIMARY KEY,
  payment_id      UUID NOT NULL REFERENCES payments(id),
  source_ref      TEXT NOT NULL,    -- capture_id or auth_id
  source_kind     TEXT NOT NULL,    -- CAPTURE | AUTH (void)
  amount_minor    BIGINT NOT NULL,
  reason          TEXT,
  gateway_ref     TEXT,
  status          TEXT NOT NULL,    -- INITIATED | REFUNDED | FAILED
  idempotency_key TEXT NOT NULL UNIQUE
);
```

### Returns

```sql
CREATE TABLE returns (
  id              UUID PRIMARY KEY,
  shipment_id     UUID NOT NULL REFERENCES shipments(id),
  reason          TEXT,
  status          TEXT NOT NULL,    -- REQUESTED | APPROVED | PICKED_UP | INSPECTED | REFUNDED | REJECTED
  refund_id       UUID,
  restock         BOOLEAN,
  inspected_at    TIMESTAMPTZ,
  reviewer_id     UUID
);
CREATE INDEX idx_returns_status ON returns(status);
```

### BuyBox cache (write-back)

```sql
CREATE TABLE buybox_winners (
  sku_id          UUID PRIMARY KEY,
  offer_id        UUID NOT NULL,
  score           DOUBLE PRECISION,
  computed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

(Authoritative copy is Redis; this table is the durable backing for cache warm-up.)

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

## The four queries that matter most

### 1. Atomic per-offer inventory decrement

This is the single most important write in the system. For each cart line in the place-order saga:

```sql
UPDATE inventory_units
   SET available = available - $qty,
       reserved  = reserved  + $qty,
       version   = version + 1,
       updated_at = now()
 WHERE seller_id = $seller_id
   AND sku_id    = $sku_id
   AND available >= $qty
RETURNING available;
```

If `affected_rows = 0`: the seller doesn't have enough stock → rollback all earlier decrements in this saga and return OUT_OF_STOCK with the failing line.

The PK `(seller_id, sku_id)` plus the predicate `available >= qty` is the natural mutex. Postgres serialises on the row-level lock; concurrent decrements queue and either succeed or fail predictably. There is no oversell.

The `reserved` column lets us account for "hold" state during the saga; on commit the order's quantity is implicitly already deducted from `available`, so we don't need a second update — this is a single CAS, not two-phase.

### 2. Place-order TXN (idempotent)

```sql
BEGIN;

-- Idempotency: if a row with this key already exists, return the existing order.
INSERT INTO orders (id, user_id, total_minor, status, address_id, idempotency_key)
VALUES (...)
ON CONFLICT (user_id, idempotency_key) DO NOTHING
RETURNING id;
-- if RETURNING is empty: SELECT existing order and return it.

INSERT INTO order_items (...) VALUES (... × N);
INSERT INTO shipments  (...) VALUES (... × M sellers);
INSERT INTO payments   (...) VALUES (...);
INSERT INTO outbox(event_type, payload) VALUES ('OrderPlaced', $...);

COMMIT;
```

The inventory decrement happens **before** this TXN (it's its own TXN per row). On commit failure of this orders TXN, a reconciliation loop releases the inventory and voids the gateway authorize.

### 3. Per-shipment capture

```sql
BEGIN;

INSERT INTO captures (id, payment_id, shipment_id, amount_minor, idempotency_key)
VALUES (...)
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

UPDATE shipments
   SET status = 'DISPATCHED',
       dispatched_at = now(),
       capture_id = $capture_id,
       version = version + 1
 WHERE id = $shipment_id
   AND status = 'PACKED';

UPDATE payments
   SET captured_minor = captured_minor + $amount,
       status = CASE
         WHEN captured_minor + $amount >= authorized_minor THEN 'CAPTURED'
         ELSE 'PARTIALLY_CAPTURED' END
 WHERE id = $payment_id;

INSERT INTO outbox(event_type, payload) VALUES ('ShipmentDispatched', $...);

COMMIT;
```

Status-guarded UPDATE on shipments ensures only PACKED shipments transition. The gateway-side capture is called *outside* the TXN with `idempotency_key = shipment_id`; the gateway dedupes; we record the result inside the TXN.

### 4. Refund (cancel or return)

```sql
INSERT INTO refunds (id, payment_id, source_ref, source_kind, amount_minor, idempotency_key)
VALUES (...)
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id;

UPDATE payments
   SET refunded_minor = refunded_minor + $amount,
       status = CASE
         WHEN refunded_minor + $amount >= captured_minor THEN 'REFUNDED'
         ELSE 'PARTIALLY_REFUNDED' END
 WHERE id = $payment_id;

-- If restockable, increment inventory:
UPDATE inventory_units
   SET available = available + $qty,
       reserved  = reserved  - $qty,
       version   = version + 1
 WHERE seller_id = $s AND sku_id = $k;
```

---

## Indexing summary

| Table | Index | Purpose |
| --- | --- | --- |
| products | (category_id) WHERE ACTIVE | Browse |
| skus | (product_id) | Variant lookup |
| listing_offers | UNIQUE (seller_id, sku_id) | One offer per seller per SKU |
| listing_offers | (sku_id) WHERE ACTIVE | BuyBox compute |
| inventory_units | PK (seller_id, sku_id) | Atomic mutex |
| orders | UNIQUE (user_id, idempotency_key) | Idempotency |
| orders | (user_id, created_at DESC) | "My orders" |
| order_items | (order_id) | Order detail |
| order_items | (shipment_id) | Shipment detail |
| shipments | (order_id) | Order detail |
| shipments | (seller_id, status) | Seller dashboard |
| payments | UNIQUE (gateway, idempotency_key) | Gateway dedup |
| captures | UNIQUE (idempotency_key) | Capture dedup |
| refunds | UNIQUE (idempotency_key) | Refund dedup |
| returns | (status) | Worker pickup |

---

## Caching

| Cache | Key | TTL | Invalidation |
| --- | --- | --- | --- |
| Product detail | `prod:productId` | 10 min | On ProductUpdated event |
| SKU + offers | `sku:skuId:offers` | 1 min | On OfferUpdated event |
| BuyBox winner | `bb:skuId` | 5 s soft, 60 s hard | On OfferUpdated / InventoryUpdated |
| Cart | Redis hash `cart:userId` | 30 days | On user mutation |
| Order summary | `ord:orderId` | 1 hr | On status change |
| Search facets | `srch:facets:query` | 30 s | TTL only |

---

## Output

```
Catalog:        Postgres (active products / skus / offers per category)
Inventory:      inventory_units row per (seller, sku) — atomic CAS, the central mutex
Cart:           Redis primary + Postgres fallback
Orders:         Postgres, sharded by user, partitioned monthly
Payment:        AUTH/CAPTURE/REFUND ledger; idempotency on every row
Returns:        async aggregate, optional restock
BuyBox:         Redis-cached, durable copy in Postgres
Hot path query: atomic UPDATE inventory_units WHERE available >= qty
Idempotency:    UNIQUE constraints everywhere money is moved
```
