# 07 · Hotel Booking — Class Diagrams

## Domain layer

```mermaid
classDiagram
  class Hotel {
    -UUID id
    -String name
    -Address address
    -double rating
    -boolean active
    +addRoomType(RoomType)
    +deactivate()
  }

  class RoomType {
    -UUID id
    -UUID hotelId
    -String name
    -int maxOccupancy
    -List~Amenity~ amenities
  }

  class RoomInventory {
    -UUID hotelId
    -UUID roomTypeId
    -LocalDate date
    -int totalRooms
    -int availableRooms
    -Money basePrice
    -boolean blocked
    +reserve(int count) bool
    +release(int count)
    +block()
  }

  class Booking {
    -UUID id
    -UUID guestId
    -UUID hotelId
    -UUID roomTypeId
    -LocalDate checkIn
    -LocalDate checkOut
    -int roomCount
    -BookingStatus status
    -PriceBreakdown priceBreakdown
    -CancellationPolicySnapshot policy
    -long version
    +confirm()
    +cancel()
    +checkIn()
    +checkOut()
    +noShow()
    +modify(newCheckIn, newCheckOut, newRoomCount)
  }

  class PriceBreakdown { <<value>> }
  class CancellationPolicySnapshot { <<value>> }
  class BookingStatus { <<enumeration>> }

  Hotel "1" *-- "*" RoomType
  Booking ..> BookingStatus
  Booking ..> CancellationPolicySnapshot
  Booking ..> PriceBreakdown
```

---

## Application services

```mermaid
classDiagram
  class BookingService {
    -BookingRepository bookings
    -InventoryService inventory
    -PricingService pricing
    -PaymentService payment
    -EventPublisher events
    -IdempotencyStore idem
    +createBooking(CreateBookingCommand)
    +cancel(UUID, ActorContext)
    +modify(UUID, ModifyCommand)
    +checkIn(UUID)
    +checkOut(UUID)
  }

  class InventoryService {
    -RoomInventoryRepository repo
    +reserveRange(hotelId, roomTypeId, dateRange, count) bool
    +releaseRange(hotelId, roomTypeId, dateRange, count)
    +blockRange(hotelId, roomTypeId, dateRange, reason)
    +bulkUpdate(hotelId, roomTypeId, ranges)
    +availabilityFor(hotelId, roomTypeId, dateRange) Map~LocalDate, Avail~
  }

  class PricingService {
    -List~PricingRule~ rules
    +quote(hotelId, roomTypeId, dateRange, occupancy) PriceBreakdown
    +signPriceToken(quote) String
    +verifyPriceToken(token) PriceBreakdown
  }

  class SearchService {
    -ElasticClient es
    -Redis cache
    +search(query) List~HotelSummary~
  }

  class HotelService { ... }
  class CancellationService { ... }

  BookingService --> InventoryService
  BookingService --> PricingService
  BookingService --> PaymentService
  BookingService --> EventPublisher
```

---

## Strategies

```mermaid
classDiagram
  class PricingRule {
    <<interface>>
    +apply(QuoteContext, PriceBreakdownBuilder)
  }
  class BasePriceRule
  class SeasonalRule
  class OccupancyRule
  class LastMinuteRule
  class LengthOfStayRule
  class PromoCodeRule
  class TaxRule

  PricingRule <|.. BasePriceRule
  PricingRule <|.. SeasonalRule
  PricingRule <|.. OccupancyRule
  PricingRule <|.. LastMinuteRule
  PricingRule <|.. LengthOfStayRule
  PricingRule <|.. PromoCodeRule
  PricingRule <|.. TaxRule

  class CancellationPolicy {
    <<interface>>
    +refundFor(Booking, Instant cancelAt) Money
  }
  class FlexiblePolicy
  class StandardPolicy
  class StrictPolicy
  class NonRefundablePolicy
  CancellationPolicy <|.. FlexiblePolicy
  CancellationPolicy <|.. StandardPolicy
  CancellationPolicy <|.. StrictPolicy
  CancellationPolicy <|.. NonRefundablePolicy
```

---

## Repositories

```mermaid
classDiagram
  class HotelRepository { <<interface>> }
  class RoomTypeRepository { <<interface>> }
  class RoomInventoryRepository {
    <<interface>>
    +findRange(hotelId, roomTypeId, from, to) List~RoomInventory~
    +decrement(hotelId, roomTypeId, date, count) bool   // CAS
    +increment(hotelId, roomTypeId, date, count)
    +block(hotelId, roomTypeId, dateRange)
  }
  class BookingRepository {
    <<interface>>
    +findById(UUID)
    +save(Booking)
    +findByIdempotencyKey(String)
  }

  class JpaRoomInventoryRepository
  class JpaBookingRepository
  RoomInventoryRepository <|.. JpaRoomInventoryRepository
  BookingRepository <|.. JpaBookingRepository
```

---

## State pattern (Booking)

```mermaid
classDiagram
  class BookingState {
    <<interface>>
    +cancel(Booking, Instant at)
    +modify(Booking, ModifyCommand)
    +checkIn(Booking)
    +checkOut(Booking)
    +noShow(Booking)
  }
  class PendingState
  class ConfirmedState
  class CheckedInState
  class CheckedOutState
  class CancelledState
  class NoShowState

  BookingState <|.. PendingState
  BookingState <|.. ConfirmedState
  BookingState <|.. CheckedInState
  BookingState <|.. CheckedOutState
  BookingState <|.. CancelledState
  BookingState <|.. NoShowState
```

We use the State pattern because:
- Cancellation logic differs significantly per state (PENDING/CONFIRMED can free-cancel; CHECKED_IN cannot).
- Modify operations are state-restricted.
- Each state encapsulates its allowed transitions.

---

## Layering

```mermaid
flowchart LR
  subgraph api
    BookingController; HotelController; SearchController
  end

  subgraph application
    BookingService; InventoryService; PricingService; SearchService; HotelService
  end

  subgraph domain
    Booking; RoomInventory; Hotel; PricingRule
    BookingState
    BookingRepository_int[<<interface>> BookingRepository]
  end

  subgraph infra
    JpaBookingRepository; JpaInventoryRepository; ElasticSearchClient; KafkaPublisher; StripeGateway
  end

  api --> application
  application --> domain
  application --> infra
  infra --> domain
```

---

## Take-aways

- **Booking** + **RoomInventory** are the two critical aggregates.
- Strategy for pricing rules + cancellation policies — both vary widely.
- State pattern for Booking lifecycle to encapsulate state-dependent logic.
- Repository abstraction keeps domain pure.
- Search is a separate read-store; eventually consistent.
