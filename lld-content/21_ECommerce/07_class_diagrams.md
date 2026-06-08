# 07 · E-Commerce — Class Diagrams

## Package layout

```
com.ecommerce
 ├── domain/        Money, Address, ids, enums, value objects
 ├── catalog/       Product, SKU, ListingOffer, CatalogService, BuyBoxStrategy
 ├── inventory/     InventoryUnit, InventoryService (atomic CAS)
 ├── cart/          Cart, CartLine, CartService
 ├── order/         Order, OrderItem, Shipment, OrderService (the saga)
 ├── payment/       Payment, Capture, Refund, PaymentGateway, PaymentService
 ├── fulfillment/   Shipment lifecycle ops, FulfilmentService
 ├── api/           DTOs, controller bindings (omitted in skeleton)
 └── store/         in-memory repositories
```

---

## Domain & value objects

```mermaid
classDiagram
    class Money {
      +amountMinor: long
      +currency: string
      +add(Money) Money
      +sub(Money) Money
      +multiply(int) Money
    }
    class Address {
      +line1: string
      +city: string
      +pincode: string
      +country: string
    }
    class OfferScore {
      +price: double
      +sla: double
      +rating: double
      +prime: boolean
      +total() double
    }
```

---

## Catalog

```mermaid
classDiagram
    class Product {
      -id: UUID
      -title: string
      -brand: string
      -categoryId: UUID
      -primaryPhoto: PhotoRef
    }
    class SKU {
      -id: UUID
      -productId: UUID
      -variantAttrs: Map~string,string~
      -gtin: string
    }
    class ListingOffer {
      -id: UUID
      -sellerId: UUID
      -skuId: UUID
      -price: Money
      -shipsInDays: int
      -status: OfferStatus
    }
    class CatalogService {
      +addProduct(Product)
      +addSku(SKU)
      +addOffer(ListingOffer)
      +getProduct(UUID) Product
      +getOffer(UUID) ListingOffer
      +listOffersForSku(UUID) List~ListingOffer~
    }
    class BuyBoxStrategy {
      <<interface>>
      +pick(List~ListingOffer~, BuyBoxContext) ListingOffer
    }
    class DefaultBuyBox {
      +pick(...) ListingOffer
    }
    Product "1" o-- "*" SKU
    SKU "1" o-- "*" ListingOffer
    BuyBoxStrategy <|.. DefaultBuyBox
    CatalogService ..> ListingOffer
    CatalogService ..> BuyBoxStrategy
```

`DefaultBuyBox` scores each candidate offer against `(price, shipsInDays, sellerRating, prime)` and returns the highest-scoring active, in-stock offer.

---

## Inventory (the heart of the buy plane)

```mermaid
classDiagram
    class InventoryUnit {
      -sellerId: UUID
      -skuId: UUID
      -available: int
      -reserved: int
      -version: long
    }
    class InventoryService {
      +reserve(List~Line~, idempKey) ReserveResult
      +release(orderId, lines)
      +commit(orderId, lines)
      +adjust(sellerId, skuId, delta, idempKey)
      +getAvailable(sellerId, skuId) int
    }
    class Line {
      +sellerId: UUID
      +skuId: UUID
      +qty: int
    }
    class ReserveResult {
      <<sealed>>
    }
    class Reserved {
      +lines: List~Line~
    }
    class Conflict {
      +blocked: List~Line~
    }
    InventoryService ..> ReserveResult
    ReserveResult <|-- Reserved
    ReserveResult <|-- Conflict
```

`InventoryService.reserve(...)` does the CAS per line in one TXN:
- For each line: `UPDATE inventory_units SET available = available - qty WHERE … AND available >= qty`.
- If any UPDATE returned 0 rows, mark conflict and *re-increment* the lines that did decrement (compensate within the same TXN).
- Return `Reserved(lines)` or `Conflict(blocked)`.

The sealed `ReserveResult` keeps the success / conflict paths exception-free in the hot path.

---

## Cart

```mermaid
classDiagram
    class Cart {
      -userId: UUID
      -lines: Map~UUID,CartLine~
      -updatedAt: Instant
      +addLine(offerId, qty, priceAtAdd)
      +updateLine(offerId, qty)
      +removeLine(offerId)
      +totalAtAdd() Money
    }
    class CartLine {
      -offerId: UUID
      -qty: int
      -priceAtAdd: Money
    }
    class CartService {
      +get(userId) Cart
      +add(userId, offerId, qty) Cart
      +update(userId, offerId, qty) Cart
      +remove(userId, offerId) Cart
      +clear(userId)
    }
    Cart "1" o-- "*" CartLine
    CartService ..> Cart
```

CartService writes both Redis (hot) and Postgres (durable). Reads come from Redis; on miss, fall back to Postgres and warm.

---

## Order (the saga)

```mermaid
classDiagram
    class Order {
      -id: UUID
      -userId: UUID
      -addressId: UUID
      -total: Money
      -status: OrderStatus
      -idempotencyKey: string
      -items: List~OrderItem~
      -shipments: List~Shipment~
      -payment: Payment
      +confirm()
      +cancel(reason)
      +markFulfilling()
      +markCompleted()
    }
    class OrderItem {
      -id: UUID
      -orderId: UUID
      -offerId: UUID
      -sellerId: UUID
      -skuId: UUID
      -qty: int
      -unitPrice: Money
      -shipmentId: UUID
      -status: OrderItemStatus
    }
    class Shipment {
      -id: UUID
      -orderId: UUID
      -sellerId: UUID
      -amount: Money
      -status: ShipmentStatus
      -awb: string
      -captureId: string
      +pack()
      +dispatch(awb, captureId)
      +markDelivered()
      +cancel(reason)
    }
    class OrderService {
      <<saga>>
      +place(userId, cartId, addressId, paymentMethodId, idemKey) Order
      +cancelOrder(orderId, reason)
      +cancelShipment(shipmentId, reason)
    }
    OrderService ..> Order
    Order "1" o-- "*" OrderItem
    Order "1" o-- "*" Shipment
    Order "1" o-- "1" Payment
    Shipment "1" o-- "*" OrderItem
```

`OrderService.place(...)` orchestrates:
1. Idempotency lookup (return cached on duplicate).
2. Hydrate cart + recompute prices.
3. Group cart lines by `seller_id` → one shipment per seller.
4. `InventoryService.reserve(...)` — atomic CAS across all lines.
5. `PaymentService.authorize(...)` — gateway hold.
6. Persist Order + OrderItems + Shipments + Payment + outbox in one TXN.
7. Return.

Each failure compensates priors (release inventory, void auth).

---

## Payment

```mermaid
classDiagram
    class Payment {
      -id: UUID
      -orderId: UUID
      -authorizedAmount: Money
      -capturedAmount: Money
      -refundedAmount: Money
      -authId: string
      -savedMethodToken: string
      -status: PaymentStatus
      -idempotencyKey: string
    }
    class Capture {
      -id: UUID
      -paymentId: UUID
      -shipmentId: UUID
      -amount: Money
      -gatewayRef: string
      -status: CaptureStatus
    }
    class Refund {
      -id: UUID
      -paymentId: UUID
      -sourceRef: string
      -sourceKind: RefundSource
      -amount: Money
      -gatewayRef: string
      -status: RefundStatus
    }
    class PaymentGateway {
      <<interface>>
      +authorize(orderId, amount, idemKey) AuthResult
      +capture(authId, amount, idemKey) CaptureResult
      +voidAuth(authId, idemKey) VoidResult
      +refund(captureId, amount, idemKey) RefundResult
      +mit(savedMethod, amount, idemKey) ChargeResult
    }
    class FakeGateway
    class PaymentService {
      -gateway: PaymentGateway
      +authorize(Order) Payment
      +captureForShipment(Payment, Shipment) Capture
      +refundForCancel(Payment, Shipment) Refund
      +refundForReturn(Return) Refund
    }
    PaymentGateway <|.. FakeGateway
    PaymentService ..> PaymentGateway
    Payment "1" o-- "*" Capture
    Payment "1" o-- "*" Refund
```

The gateway abstraction has five operations; the service composes them based on context. Refund routing logic:
- If shipment cancelled before capture → `voidAuth(authId, partialAmount)`.
- If shipment cancelled after capture → `refund(captureId, amount)`.
- If return refund → `refund(captureId, amount)` (always against captured charge).

---

## Fulfilment

```mermaid
classDiagram
    class FulfilmentService {
      +pack(shipmentId)
      +dispatch(shipmentId, awb, carrier) Capture
      +markDelivered(shipmentId)
      +carrierWebhook(event)
    }
    class CarrierAdapter {
      <<interface>>
      +book(shipment) AwbResult
      +track(awb) TrackingStatus
    }
    class BlueDartAdapter
    class DelhiveryAdapter
    class MockCarrier
    CarrierAdapter <|.. BlueDartAdapter
    CarrierAdapter <|.. DelhiveryAdapter
    CarrierAdapter <|.. MockCarrier
    FulfilmentService ..> CarrierAdapter
    FulfilmentService ..> PaymentService
```

Dispatch triggers capture: a single `dispatch(shipmentId, …)` call performs (a) status transition PACKED→DISPATCHED, (b) capture on the gateway, (c) outbox event, all in one TXN.

---

## Returns

```mermaid
classDiagram
    class Return {
      -id: UUID
      -shipmentId: UUID
      -orderItemIds: List~UUID~
      -reason: string
      -status: ReturnStatus
      -refundAmount: Money
      -restock: boolean
      +approve(reviewerId)
      +inspect(passed, restock)
      +complete(refundId)
    }
    class ReturnService {
      +request(userId, shipmentId, items, reason) Return
      +approve(returnId, reviewerId)
      +inspect(returnId, passed, restock)
    }
    ReturnService ..> Return
    ReturnService ..> PaymentService
    ReturnService ..> InventoryService
```

`inspect(passed=true)` triggers `PaymentService.refundForReturn(...)` and (if restockable) `InventoryService.adjust(...)` to put the unit back.

---

## Why these abstractions

| Abstraction | Why |
| --- | --- |
| `InventoryService` returning sealed `ReserveResult` | Two paths (Reserved / Conflict) without exceptions on the hot place-order path |
| `BuyBoxStrategy` interface | A/B test scoring algorithms; per-category overrides |
| `PaymentGateway` interface with `mit` separate | Delayed capture / refund needs MIT; modeling as a separate op makes intent explicit |
| `Order` immutable lines post-CONFIRMED | Edits would corrupt inventory accounting; force cancel + reorder |
| `Shipment` as own aggregate sub-unit | Per-shipment capture, status, cancel — independent lifecycle |
| `CarrierAdapter` interface | Adding a new courier is a new class, no edits to FulfilmentService |
| `Capture` / `Refund` as first-class entities (not just status flags on Payment) | Clean ledger; multi-shipment / partial refund tracking |

---

## Output

```
Catalog:    Product → SKU → ListingOffer + BuyBoxStrategy
Inventory:  InventoryUnit + InventoryService(reserve, release, commit, adjust)
Cart:       Cart, CartLine + CartService(add, update, remove)
Order:      aggregate + OrderService(saga: place, cancel)
Shipment:   sub-aggregate; pack → dispatch → delivered
Payment:    PaymentGateway interface + PaymentService(auth, capture, void, refund, mit)
Fulfilment: per-carrier adapter pattern
Returns:    ReturnService(request, approve, inspect → refund + optional restock)
```
