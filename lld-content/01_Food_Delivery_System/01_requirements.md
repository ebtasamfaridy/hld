# 01 · Food Delivery — Requirements

## Problem statement

Design a food delivery system like Swiggy / DoorDash that allows:

1. Customers to browse restaurants and place orders.
2. Restaurants to receive, accept, and prepare orders.
3. Drivers to pick up and deliver orders.
4. The platform to track the order end-to-end.

We will design the **backend**, not the mobile apps.

---

## Functional requirements

### Core (in scope)

**Customer-facing**
- Browse nearby restaurants by location.
- View a restaurant's menu (categories, items, availability).
- Add items to a cart from a single restaurant.
- Place an order with a delivery address and payment method.
- Cancel an order before it transitions to `PREPARING`.
- Track an order's status and the driver's live location.
- View past orders.

**Restaurant-facing**
- Receive a new order notification.
- Accept or reject the order within a window.
- Mark items prepared / order ready for pickup.
- Mark menu items as out of stock.

**Driver-facing**
- Go online / offline.
- Receive a delivery offer; accept or reject within 15s.
- Update status (PICKED_UP, OUT_FOR_DELIVERY, DELIVERED).
- View earnings (read-only here).

**Platform-facing**
- Match an order to the best driver.
- Compute price (subtotal, taxes, delivery fee, discounts).
- Surge pricing when demand exceeds supply.
- Notify all parties on state changes (push / SMS).
- Provide ETAs to customer.

### Extensions (mentioned, not built today)

- Multi-restaurant cart.
- Scheduled deliveries.
- Subscription (Swiggy One / DashPass).
- Promotions and referrals.
- Ratings and reviews.
- Rider-customer chat / call.
- Recommendation engine.

### Out of scope

- The mobile apps themselves.
- The payment gateway internals (we integrate via `PaymentGatewayClient`).
- Restaurant onboarding flows.
- Analytics / data warehouse.
- Multi-currency (assume INR / USD for V1).

---

## Non-functional requirements

| NFR | Target | Why it matters |
| --- | --- | --- |
| **Latency p99** | < 200 ms for order placement, < 100 ms for menu read | UX; cart abandonment grows with latency |
| **Availability** | 99.95 % | Lost orders = lost revenue + brand damage |
| **Throughput** | 200 RPS sustained, 1000 RPS peak (lunch / dinner) | Scale to a major city's traffic |
| **Consistency** | Strong for orders & payments, eventual for menus, location streams | Money-critical paths must be correct |
| **Durability** | No order loss after 200 OK | Acknowledged orders must survive crashes |
| **Scalability** | 10× growth in 12 months | Must scale horizontally |
| **Geo-coverage** | Multi-city, multi-region | Driver and restaurant data is geo-partitioned |
| **Security** | PII encrypted at rest, payment details never stored | PCI-DSS compliance |
| **Maintainability** | New pricing rule deployed in < 1 day | Strategy pattern, feature flags |

---

## Key clarifications you should ask the interviewer

1. **Geographic scope** — single city or multi-city? Multi-region affects sharding and latency.
2. **Multi-restaurant cart?** Major design impact; usually defer to V2.
3. **Scheduled orders?** Adds time-based dispatch.
4. **Payment** — pre-paid only, or COD?
5. **Cancellation window** — until kitchen accepts, until preparing starts, or never?
6. **Refund flow** — instant or 7-day? Affects payment system.
7. **Driver model** — gig drivers (1099) or employees? Affects driver-state lifecycle.
8. **Tip / contactless / instructions** — should we model these as first-class?

State the **assumption** you're making for each:

> "Single city, prepaid only, cancellable until PREPARING, multi-restaurant out of scope."

---

## Edge cases you must handle

| Edge case | Handling |
| --- | --- |
| Restaurant rejects order after acceptance | Refund customer, alert support |
| Item out of stock at acceptance time | Restaurant proposes substitution or cancellation |
| Driver goes offline mid-delivery | Reassign or alert support |
| No driver available within window | Escalate radius; if still none, cancel and refund |
| Payment fails after order placed | Voluntary retry → cancel after 3 fails |
| Customer enters wrong address after dispatch | Driver-customer call; address change has fee or rejected |
| Duplicate order (network retry) | Idempotency key |
| Restaurant offline / closed | Reject order at placement |
| Surge expires during order placement | Lock surge factor at cart creation |

---

## Actors

```
Customer        ─ places, cancels, tracks orders
Restaurant      ─ accepts, prepares, marks ready
Driver          ─ accepts delivery, picks up, delivers
Admin / Support ─ resolves disputes, refunds, comp
PaymentGateway  ─ external; charge / refund / webhook
NotificationSvc ─ external; push, SMS, email
MapsService     ─ external; ETA, geocoding, routing
DispatchService ─ internal; matches drivers to orders
AnalyticsETL    ─ internal; consumes domain events
```

8 actors. Each implies an API or integration surface.

---

## V1 vs V2 / V3

| Feature | V1 | V2 | V3 |
| --- | --- | --- | --- |
| Single-restaurant order | ✓ | | |
| Live tracking | ✓ | | |
| Surge pricing | ✓ | | |
| Cancellation | ✓ | | |
| Order batching (1 driver, 2 orders) | | ✓ | |
| Multi-restaurant cart | | ✓ | |
| Subscription (free delivery) | | ✓ | |
| Scheduled orders | | ✓ | |
| Self-pickup | | | ✓ |
| Recommendations | | | ✓ |

The V1 design must not block V2/V3 features. We'll see how in `13_extensions_and_tradeoffs.md`.

---

## Output of this step

```
Actors:        Customer, Restaurant, Driver, Admin, PaymentGateway, Notification, Maps, Dispatch
Core FR:       browse, place, cancel, track, dispatch, complete order
NFR:           p99 < 200ms, 1000 RPS peak, strong consistency on orders, 99.95% uptime
Out of Scope:  payment internals, mobile apps, analytics, multi-restaurant cart
Extensions:    batching, scheduled orders, subscriptions, ratings
```

Move to `02_capacity_estimation.md`.
