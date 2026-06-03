# 08 · Food Delivery — Sequence Diagrams

## 1. Place order — happy path

```mermaid
sequenceDiagram
  autonumber
  participant CA as Customer App
  participant API as API Gateway
  participant OS as OrderService
  participant IDM as IdempotencyStore
  participant CAT as CatalogService
  participant INV as InventoryService
  participant PR as PricingService
  participant PAY as PaymentService
  participant DB as Postgres
  participant OB as Outbox
  participant K as Kafka

  CA->>API: POST /v1/orders {Idempotency-Key: K1}
  API->>OS: placeOrder(cmd)
  OS->>IDM: lookup(K1)
  IDM-->>OS: not found
  OS->>CAT: validate(restaurant, items)
  CAT-->>OS: ok
  OS->>INV: reserve(items)
  INV-->>OS: ok
  OS->>PR: compute(cart, surge, promo)
  PR-->>OS: priceBreakdown
  OS->>PAY: charge(amount, paymentKey)
  PAY-->>OS: captured
  OS->>DB: BEGIN
  OS->>DB: insert order
  OS->>DB: insert items
  OS->>DB: insert outbox(OrderPlaced)
  OS->>DB: COMMIT
  DB-->>OS: ok
  OS-->>API: Order{PLACED}
  API-->>CA: 201 Created
  Note over OB,K: Background outbox poller publishes OrderPlaced to Kafka
  OB->>K: publish OrderPlaced
```

Key insight: the order persistence and the outbox event happen in the **same transaction**. The Kafka publish happens later, asynchronously, but durably.

---

## 2. Place order — payment failure (compensation)

```mermaid
sequenceDiagram
  autonumber
  participant OS as OrderService
  participant INV as InventoryService
  participant PAY as PaymentService

  OS->>INV: reserve(items)
  INV-->>OS: ok
  OS->>PAY: charge(amount)
  PAY-->>OS: declined
  OS->>INV: release(items)
  OS-->>OS: throw 402 PAYMENT_DECLINED
```

If charging fails after inventory reservation, we explicitly release inventory. Otherwise items get stuck "reserved."

---

## 3. Restaurant accepts order

```mermaid
sequenceDiagram
  participant RA as Restaurant POS
  participant API as API Gateway
  participant OS as OrderService
  participant DB as Postgres
  participant OB as Outbox
  participant K as Kafka
  participant DISP as DispatchService

  RA->>API: POST /v1/orders/{id}:accept
  API->>OS: confirm(id)
  OS->>DB: SELECT order WHERE id=? (read with version)
  DB-->>OS: order(version=0)
  OS->>OS: order.confirm()  // PLACED -> CONFIRMED
  OS->>DB: UPDATE order SET status='CONFIRMED', version=1 WHERE id=? AND version=0
  DB-->>OS: 1 row updated
  OS->>OB: write OrderConfirmed event
  OS-->>API: ok
  Note over OB,K: poller publishes
  OB->>K: OrderConfirmed
  K->>DISP: consume → start dispatch
```

Optimistic lock ensures we never overwrite a concurrent transition.

---

## 4. Dispatch — find and offer driver

```mermaid
sequenceDiagram
  autonumber
  participant DISP as DispatchService
  participant FIND as DriverFinder (Redis Geo)
  participant SCO as ScoringStrategy
  participant DR as DriverRepository
  participant ASN as AssignmentRepository
  participant PUSH as PushService
  participant DA as Driver App

  DISP->>FIND: nearby(pickup, 3km, 10)
  FIND-->>DISP: candidate driver IDs
  DISP->>DR: load drivers (FOR UPDATE SKIP LOCKED)
  DR-->>DISP: 5 idle drivers
  loop for each candidate
    DISP->>SCO: score(driver, order)
  end
  DISP->>DR: pick top, status: IDLE -> OFFER_PENDING (CAS via version)
  DR-->>DISP: ok
  DISP->>ASN: insert Assignment(OFFERED, expires=15s)
  DISP->>PUSH: send DELIVERY_OFFER
  PUSH->>DA: push notification
  Note over DISP,DA: 15s timer
  alt Driver accepts within 15s
    DA->>DISP: POST /assignments/{id}:accept
    DISP->>DR: status: OFFER_PENDING -> BUSY
    DISP->>ASN: status: OFFERED -> ACCEPTED
  else Timeout
    DISP->>DR: status: OFFER_PENDING -> IDLE
    DISP->>ASN: status: OFFERED -> EXPIRED
    Note right of DISP: try next candidate
  end
```

`FOR UPDATE SKIP LOCKED` lets parallel dispatchers pick **different** candidates safely.

---

## 5. Live tracking flow

```mermaid
sequenceDiagram
  participant DA as Driver App
  participant DS as DriverService
  participant R as Redis Geo
  participant K as Kafka
  participant TS as TrackingService
  participant CA as Customer App

  Note over CA,TS: Customer subscribes via WebSocket (orderId)
  CA->>TS: WS connect /v1/orders/{id}/track

  loop every 4s
    DA->>DS: POST /drivers/me/locations
    DS->>R: GEOADD driver:locations driverId lng lat
    DS->>K: publish driver-locations
    K->>TS: consume
    TS->>CA: WS push {DRIVER_LOC}
  end
```

Customer app gets sub-second latency updates. Redis is the source-of-truth for "where is driver right now"; Kafka is the durable bus for downstream.

---

## 6. Customer cancels order

```mermaid
sequenceDiagram
  participant CA as Customer App
  participant OS as OrderService
  participant DB as Postgres
  participant DR as DriverRepository
  participant ASN as AssignmentRepository
  participant PAY as PaymentService

  CA->>OS: POST /orders/{id}:cancel
  OS->>DB: SELECT order WHERE id=? (with version)
  DB-->>OS: order(status=CONFIRMED, v=1)
  OS->>OS: order.cancel()      // guard: status in {PLACED, CONFIRMED}
  OS->>DB: UPDATE WHERE id=? AND version=1
  alt If assignmentId not null
    OS->>ASN: cancel assignment
    OS->>DR: driver IDLE
  end
  OS->>PAY: refund(orderId, paymentKey, full)
  PAY-->>OS: refund queued
  OS-->>CA: 200 {status=CANCELLED, refund_status=QUEUED}
```

Note: refund is **async**; the API doesn't wait for actual money movement.

---

## 7. Order delivered

```mermaid
sequenceDiagram
  participant DA as Driver App
  participant DISP as DispatchService
  participant OS as OrderService
  participant K as Kafka
  participant SETT as SettlementService

  DA->>DISP: POST /assignments/{id}:deliver {otp:1234}
  DISP->>OS: mark order DELIVERED
  OS->>OS: validate state, version CAS
  OS->>K: publish OrderDelivered
  K->>SETT: credit driver earnings
  K->>SETT: capture restaurant payout
  DISP-->>DA: 200 OK
```

Settlement is a downstream consumer. Order delivery does not block on it.

---

## 8. Stuck-order monitoring

```mermaid
sequenceDiagram
  participant CRON as StuckOrderCron
  participant DB as Postgres
  participant ALERT as Alerting
  participant OPS as OpsConsole

  loop every 1 min
    CRON->>DB: SELECT * FROM orders WHERE status IN ('PLACED','CONFIRMED','PREPARING') AND created_at < now() - interval '20 min'
    DB-->>CRON: stuck list
    alt any stuck
      CRON->>ALERT: page on-call
      CRON->>OPS: surface in dashboard
    end
  end
```

Required for any production system. Often forgotten in interviews.

---

## What these diagrams reveal

- The **transaction boundary** is always around the local DB write + outbox.
- All cross-service work is **async via Kafka**.
- All state-machine transitions use **optimistic locking** via `version`.
- Compensation paths (refund, inventory release) are **explicit**.
- Background workers fill the gap (cron for stuck orders, outbox poller for events).

If you can draw and explain these 8 diagrams, you have demonstrated end-to-end understanding of the system.
