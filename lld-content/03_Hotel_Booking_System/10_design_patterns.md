# 10 · Hotel Booking — Design Patterns

## 1. Strategy — pricing rules

Like the other systems, but pricing for hotels is rich.

```java
public interface PricingRule { void apply(QuoteContext ctx, PriceBreakdownBuilder b); }

class BasePriceRule implements PricingRule {
  // for each night, b.addNight(date, basePrice)
}

class SeasonalRule implements PricingRule {
  // multiply nights in season range
}

class OccupancyRule implements PricingRule {
  // if hotel-wide availability < 20%, +10%
}

class LastMinuteRule implements PricingRule {
  // if checkIn within 24h, -15%
}

class LengthOfStayRule implements PricingRule {
  // 7+ nights, -10%
}

class PromoCodeRule implements PricingRule {
  // apply promo from PromoEngine
}

class TaxRule implements PricingRule {
  // GST 12% on subtotal
}
```

`PricingService` calls them in order:

```java
public PriceBreakdown quote(QuoteContext ctx) {
  PriceBreakdownBuilder b = new PriceBreakdownBuilder(ctx);
  for (PricingRule r : rules) r.apply(ctx, b);
  return b.build();
}
```

Order matters. Documented in code.

---

## 2. Strategy — cancellation policy

```java
public interface CancellationPolicy {
  Money refundFor(Booking b, Instant cancelAt);
}

class FlexiblePolicy implements CancellationPolicy {
  public Money refundFor(Booking b, Instant cancelAt) {
    long hoursBefore = ChronoUnit.HOURS.between(cancelAt, b.checkInInstant());
    if (hoursBefore >= 48) return b.totalPrice();
    return b.totalPrice().multiply(0.5);
  }
}

class StrictPolicy implements CancellationPolicy {
  public Money refundFor(Booking b, Instant cancelAt) {
    long days = ChronoUnit.DAYS.between(cancelAt, b.checkInInstant());
    if (days >= 7) return b.totalPrice();
    return Money.zero(b.currency());
  }
}

class NonRefundablePolicy implements CancellationPolicy {
  public Money refundFor(Booking b, Instant cancelAt) {
    return Money.zero(b.currency());
  }
}
```

Each policy is an algorithm. The Booking snapshots which policy applies at booking time, so changes to hotel policies don't affect existing bookings.

---

## 3. State pattern — Booking lifecycle

Justified in `09_state_machines.md`. Behavior diverges enough across states (cancel allowed, cancel costs, modify allowed) that explicit state classes pay off.

---

## 4. Repository — persistence

```java
public interface BookingRepository {
  Optional<Booking> findById(UUID id);
  Booking save(Booking b);
  Optional<Booking> findByIdempotencyKey(String key);
  List<Booking> findOverlapping(UUID hotelId, UUID roomTypeId, LocalDate from, LocalDate to);
}
```

The `findOverlapping` is for hotel admin tools (block dates, see who is affected).

---

## 5. Observer / Pub-Sub — events

Outbox + Kafka. Every booking transition publishes an event.

Consumers:
- `NotificationService` — confirmation emails / SMS.
- `SearchIndexer` — keep ES up to date.
- `SettlementService` — accrue payouts.
- `AnalyticsETL` — funnel metrics.

---

## 6. Command pattern — every booking mutation

```java
public record CreateBookingCommand(
  UUID guestId, UUID hotelId, UUID roomTypeId,
  LocalDate checkIn, LocalDate checkOut, int roomCount,
  String priceToken, UUID paymentMethodId,
  String idempotencyKey
) {}

public record ModifyBookingCommand(UUID bookingId, LocalDate newCheckIn, LocalDate newCheckOut, int newRoomCount) {}
public record CancelBookingCommand(UUID bookingId, String reason, Instant at) {}
```

Commands flow into BookingService methods.

---

## 7. Decorator — cross-cutting

We wrap services with logging, metrics, retries, distributed tracing decorators.

```java
BookingService svc = new TracingBookingService(
                       new MetricsBookingService(
                         new LoggingBookingService(
                           new CoreBookingService(...))));
```

---

## 8. Builder — Booking construction

```java
Booking b = Booking.builder()
              .guest(guestId)
              .hotel(hotelId)
              .roomType(rtId)
              .checkIn(...).checkOut(...)
              .roomCount(1)
              .priceBreakdown(price)
              .policySnapshot(policy)
              .idempotencyKey(key)
              .build();
```

`.build()` validates invariants (checkOut > checkIn, roomCount >= 1, currency consistent).

---

## 9. Chain of Responsibility — booking validation

```java
List<BookingValidator> chain = List.of(
  new AuthValidator(),
  new RateLimitValidator(),
  new GuestEligibilityValidator(),
  new HotelActiveValidator(),
  new RoomTypeAvailabilityHintValidator(),    // quick check, not authoritative
  new PriceTokenValidator(),                  // HMAC + expiry
  new PaymentMethodValidator()
);
```

If any fails, short-circuit with a typed error.

---

## 10. Factory — pricing/policy creation

We have a `PricingRulesFactory` that composes the rule list per hotel:

```java
public class PricingRulesFactory {
  public List<PricingRule> rulesFor(Hotel hotel, RoomType rt) {
    List<PricingRule> rules = new ArrayList<>();
    rules.add(new BasePriceRule());
    if (hotel.hasSeasonalPricing()) rules.add(new SeasonalRule(hotel.seasonRanges()));
    rules.add(new OccupancyRule());
    rules.add(new LastMinuteRule());
    rules.add(new LengthOfStayRule());
    rules.add(new PromoCodeRule(promoEngine));
    rules.add(new TaxRule(hotel.region()));
    return rules;
  }
}
```

Hotels can have different rule sets. Adding a new pricing model = adding a class + adjusting the factory.

---

## 11. Adapter — external integrations

```java
public interface MapsService { Coordinates geocode(String address); }
public interface PaymentGateway { Payment authorize(...); Payment capture(...); Payment refund(...); }
public interface SearchClient { void index(HotelDocument); List<HotelDocument> query(SearchQuery); }
```

We never let our domain depend on Google / Stripe / Elasticsearch directly. Adapters hide the vendor.

---

## 12. Saga — for cross-service consistency

The booking flow spans BookingService, InventoryService, PaymentService. We use either:

- **Single DB transaction** (works because all are inside Postgres) — simpler.
- **Saga with compensations** if we split inventory into a separate service with its own DB.

For V1 we keep them in the same DB. For V2 with microservices, we use **orchestrated saga**:

```
1. BookingSaga.start()
2. reserveInventory   (compensate: releaseInventory)
3. authorizePayment   (compensate: voidAuth)
4. confirmBooking     (no compensation; final)
   on any failure, run compensations in reverse
```

Mention this when asked about distributed transactions.

---

## SOLID compliance

Same logic as other systems:
- **S**: each rule, validator, policy is one class one job.
- **O**: adding a hotel pricing rule or cancellation policy = new class.
- **L**: every Strategy honors its interface.
- **I**: small repo and gateway interfaces.
- **D**: services depend on interfaces; concretes wired in composition root.

---

## What we deliberately avoided

- **Inheritance hierarchy for room types** (DeluxeRoom extends Room) — over-engineering. RoomType is a data class with config.
- **Storing Cart server-side** — V1 doesn't have a cart; the booking is created from a price quote in one shot. If we add cart later, Redis with TTL.
- **Singleton services** — DI is far cleaner.
