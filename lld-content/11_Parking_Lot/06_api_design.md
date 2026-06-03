# 06 · Parking Lot — API Design

## Customer-facing (REST)

```http
GET  /v1/lots/{id}/availability
→ { "by_type": { "COMPACT": 142, "LARGE": 38, "EV": 4 } }

POST /v1/lots/{id}/reservations
{ "plate": "MH04AB1234", "vehicle_type": "CAR",
  "starts_at": "2026-04-29T14:00Z", "ends_at": "2026-04-29T18:00Z" }
→ 201 { "id": "...", "spot_id": "...", "status": "HELD",
        "amount_due": 200, "expires_at": "2026-04-29T13:30Z" }

POST /v1/reservations/{id}/confirm     # Idempotency-Key: ...
{ "payment_method": "CARD", "payment_token": "..." }
→ 200 { "status": "CONFIRMED" }
```

## Gate-side API (programmatic / hardware)

```java
public interface EntryGate {
    /** Vehicle pulled up; allocate a spot, issue ticket. */
    EntryResult requestEntry(String plate, VehicleType type);
}

public sealed interface EntryResult permits EntryResult.Admitted, EntryResult.LotFull {
    record Admitted(TicketId ticketId, SpotId spot, String displayMessage) implements EntryResult {}
    record LotFull(VehicleType vehicleType) implements EntryResult {}
}

public interface ExitGate {
    /** Driver scanned ticket; compute fee. */
    PreExitQuote quote(TicketId ticketId, Instant now);
    /** After payment success, free the spot and open the barrier. */
    ExitResult settle(TicketId ticketId, PaymentResult payment);
}
```

The gate is the **ports & adapters** boundary. The backend exposes intents; the gate hardware drives the physical barrier.

## Operator API

```http
POST /v1/lots/{id}/operations/lost-ticket
{ "plate": "MH04AB1234" }
→ { "amount_due": 1500, "ticket_id": "..." }

POST /v1/lots/{id}/operations/manual-override
{ "ticket_id": "...", "reason": "barrier-fault" }

PATCH /v1/lots/{id}/pricing
{ "strategy": "TIERED", "config": { ... } }
```

## Idempotency

- `Idempotency-Key` header on `confirm` and `manual-override`.
- Repeated `requestEntry` for the same plate within 60 s returns the existing ACTIVE ticket — no double-allocate.
- `settle` is keyed by `ticket_id`; once `CLOSED`, a repeat returns the original receipt.

## Errors

```jsonc
HTTP/1.1 409
{ "type": ".../lot-full",  "title": "No free LARGE spots in lot", "vehicle_type": "TRUCK" }

HTTP/1.1 402
{ "type": ".../payment",   "title": "Payment required", "amount_due_minor": 4500 }

HTTP/1.1 410
{ "type": ".../reservation-expired", "title": "Reservation expired", "id": "..." }
```

## Output

```
REST:       /availability, /reservations (HOLD + CONFIRM), operator endpoints
Gate API:   requestEntry / quote+settle (hardware integration boundary)
Idempotent: requestEntry within 60s, confirm/settle keyed by id
Errors:     lot-full, payment, reservation-expired (typed)
```
