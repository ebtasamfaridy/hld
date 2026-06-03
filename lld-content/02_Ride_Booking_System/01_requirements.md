# 01 · Ride Booking — Requirements

## Problem statement

Design an on-demand ride-hailing platform like Uber. A rider requests a ride from point A to point B. A driver is matched, drives to pickup, takes the rider to the drop, and the trip is paid for.

We design the **backend**.

---

## Functional requirements

### Core (in scope)

**Rider-facing**
- Request a ride with pickup, drop, ride type (`STANDARD`, `XL`, `POOL`).
- See estimated fare and ETA before confirming.
- Cancel before driver arrives (with cancellation fee policy).
- Live-track the assigned driver to pickup.
- See driver's name, photo, vehicle plate, rating.
- Pay via stored payment method.
- Rate the trip; tip the driver.
- View ride history.

**Driver-facing**
- Go online / offline.
- Receive a trip offer; accept / decline within 15 s.
- Navigate to pickup → start trip → drop.
- See earnings.
- Trigger SOS / emergency.

**Platform-facing**
- Match drivers to ride requests in < 5 s.
- Compute fare based on distance, time, surge, ride type.
- Apply surge multiplier per zone, per minute.
- Re-dispatch if driver doesn't accept or cancels.
- Track ride end-to-end for safety.
- Handle no-show: rider absent at pickup.

### Extensions (acknowledged, not built)

- Pool / shared rides.
- Scheduled / pre-booked rides.
- Multi-stop trips.
- Corporate / B2B billing.
- Auto-rickshaw / 2-wheeler categories with different vehicle types.
- Heatmaps for drivers.
- Subscription (Uber One).

### Out of scope

- Mobile apps.
- Payment processor internals.
- Driver onboarding & background check workflow.
- Maps / routing engine internals (we integrate via `MapsClient`).

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Match latency | p95 < 5 s | Riders abandon if slow |
| Booking p99 | < 250 ms | UX |
| Geo query latency | < 50 ms | Match engine bottleneck |
| Driver location updates | 1 / 4 s, 1 M concurrent | Real-time tracking |
| Availability | 99.95 % | Trust |
| Strong consistency | rides, payments, driver state | Money-critical |
| Eventual consistency | trip stats, heatmaps, surge factor | Acceptable lag |
| Scale | 10 M rides/day, peak 5 K/sec | Major-city scale |
| Security | E2E encrypted SOS, PII at rest | Safety + compliance |

---

## Actors

```
Rider           - books rides
Driver          - takes rides
DispatchService - matches
PricingService  - computes fare incl. surge
TrackingService - live driver/ride location
PaymentGateway  - external; charge / refund
MapsService     - external; routing, ETA
NotificationSvc - push, SMS
SafetyService   - SOS, anomaly detection
Admin / Support - disputes, comp, refunds
```

10 actors. Each is an API surface or integration point.

---

## Edge cases

| Case | Handling |
| --- | --- |
| Driver accepts then cancels | Penalty; reassign; rider stays in queue |
| Rider cancels after driver dispatch | Cancel fee policy; driver compensated for distance |
| No driver available within radius | Expand radius; if still none, suggest alternative or fail |
| Driver goes offline during ride | Page support; SOS auto-trigger if no contact in 5 min |
| GPS spoofing / wrong location | Anomaly detection on speed/heading; flag for review |
| Surge stuck high after demand fades | Time-decay function; max 30 min before re-evaluation |
| Two ride requests assigned to one driver | Optimistic lock + state machine prevents (covered in concurrency) |
| Rider doesn't show up at pickup | Driver waits 5 min, no-show fee, ride cancelled |
| Payment method expired | Block ride request; prompt to update |

---

## Output

```
Actors:        Rider, Driver, Dispatch, Pricing, Tracking, Maps, Payment, Notification, Safety, Admin
Core FR:       request, match, track, complete, pay, rate
NFR:           match p95 < 5s, 5K bookings/sec peak, strong on rides+payments
Out of Scope:  apps, payment internals, maps, onboarding
Extensions:    pool, scheduled, multi-stop, B2B, subscription
```
