# 06 · Vending Machine — API Design

Two API surfaces:
1. **Customer-facing** (touchscreen / hardware buttons → controller).
2. **Operator-facing** (mobile app or service panel → controller / fleet backend).

## Customer-facing — programmatic

```java
public interface VendingMachine {
    InventoryView browse();
    void selectProduct(SlotCode slot);
    void insertCoin(Denomination denom);
    void cancel();
    State state();
}
```

The state machine ensures only valid operations succeed (others throw `IllegalStateException`). The UI shows / hides buttons based on `state()`.

## Operator-facing — programmatic

```java
public interface OperatorPanel {
    void enterMaintenance(String operatorId);
    void exitMaintenance();
    void refillSlot(SlotCode slot, int delta, Money price);
    void refillCash(Denomination denom, int countDelta);
    Money collectCash();              // returns total collected, drains cash
    AuditExport exportAudit(Instant from, Instant to);
    void setPrice(SlotCode slot, Money newPrice);
}
```

Operator must authenticate (PIN, RFID badge). All operations are logged to audit with `operator_id`.

## V2 — Fleet REST API

```http
GET  /v1/machines                          # list
GET  /v1/machines/{id}                     # status + last-seen + inventory
GET  /v1/machines/{id}/inventory           # current slots + counts
GET  /v1/machines/{id}/audit?from=&to=&kind=
POST /v1/machines/{id}/restart             # remote command
POST /v1/machines/{id}/maintenance         # toggle
POST /v1/operators                         # CRUD
GET  /v1/reports/sales?from=&to=&machine=
```

Heartbeat from machines:
```http
POST /v1/machines/{id}/heartbeat
{ "fw_version": "...", "uptime_s": ..., "alerts": [...] }
```

Audit upload:
```http
POST /v1/machines/{id}/audit
[ {seq:N, kind:..., ...}, ... ]
→ 200 { "next_seq": N+1 }
```

Idempotent on `(machine_id, seq)`.

## Errors

```jsonc
HTTP/1.1 409
{
  "type": ".../out-of-stock",
  "title": "Slot out of stock",
  "slot": "A1"
}

HTTP/1.1 422
{
  "type": ".../no-change",
  "title": "Cannot make change",
  "amount_required": 65,
  "available_denominations": { "100": 5, "500": 0 }
}
```

## Idempotency

Every customer-facing operation is **state-driven** — duplicate `selectProduct(A1)` while in `ProductSelectedState(A1)` is a no-op. Duplicate `insertCoin` adds the coin (it's a real coin); the hardware acceptor is the dedup point (one coin = one event).

For card / UPI: idempotency keys at the gateway layer.

## Output

```
Customer:  selectProduct / insertCoin / cancel / browse / state
Operator:  maintenance, refill, collect, set price, export audit
Fleet V2:  REST for status, audit upload, remote commands
Errors:    out-of-stock, no-change, hardware-error (typed)
```
