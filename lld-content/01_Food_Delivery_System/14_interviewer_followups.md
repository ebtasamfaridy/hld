# 14 · Food Delivery — Interviewer Follow-ups (with Ideal Answers)

> Practice these out loud. 90 seconds per answer.

---

## Q1. "Two customers tap 'Place Order' for the last available item at the same time. What happens?"

**Ideal answer:**

> Inventory decrement is a single SQL statement:
>
> ```sql
> UPDATE menu_item_inventory SET stock_count = stock_count - 1
> WHERE menu_item_id = ? AND stock_count >= 1;
> ```
>
> One row updates; the other returns 0 rows. The losing request gets `409 ITEM_OUT_OF_STOCK` with a friendly message.
>
> If both requests had the same idempotency key (a retry from one user), the UNIQUE constraint on `idempotency_key` ensures only one order is created. The second receives the same response.
>
> No locks held; no driver assignment until inventory is confirmed; no payment captured for the loser.

---

## Q2. "How would you prevent the same driver from being offered two orders simultaneously?"

**Ideal answer:**

> Driver has a state machine: `IDLE → OFFER_PENDING → BUSY` (or back to `IDLE` on reject/expire). The transition uses optimistic locking via a `version` column.
>
> The dispatcher's selection query is `SELECT ... FROM drivers WHERE status='IDLE' ORDER BY distance LIMIT 5 FOR UPDATE SKIP LOCKED`. SKIP LOCKED guarantees parallel dispatchers pick **different** drivers without blocking each other.
>
> When the dispatcher transitions a driver to `OFFER_PENDING`, it asserts the version matches. If two dispatchers somehow target the same driver, the second update sees a version mismatch and retries with another candidate.

---

## Q3. "What if the payment was captured but the order DB write failed?"

**Ideal answer:**

> This is the classic dual-write problem. Three defenses:
>
> 1. **Order before payment**: in our flow, we charge **after** building the in-memory Order but **before** persisting. If charge succeeds and persistence fails, the order DB INSERT can be retried (idempotent on the order id we generated).
> 2. **Outbox pattern**: the OrderPlaced event is in the same DB transaction as the order row. If the DB write succeeds, the event is durable and side effects fire. If it fails, the payment was already captured — we surface this and run a **reconciliation job** every 5 min that looks for `payments` without a matching successful `orders` row, and either retries persistence or refunds.
> 3. **Idempotent payment**: the gateway charge call uses the order's idempotency key. Retrying never double-charges.
>
> In an interview, mentioning all three (especially reconciliation) is the staff move.

---

## Q4. "Walk me through how you'd add 'live driver tracking' to the customer app."

**Ideal answer:**

> Driver app pushes location every ~4 s to `POST /v1/drivers/me/locations`. The server writes to Redis Geo (`GEOADD driver:locations`) and publishes to Kafka.
>
> The customer app, after `OUT_FOR_DELIVERY`, opens a WebSocket to `/v1/orders/{id}/track`. The TrackingService consumes Kafka, filters by orderId → driverId, and pushes location updates over the WebSocket. We also push state changes (`STATUS`, `ETA`).
>
> For scale, we put a fanout layer (or a managed WS gateway) between the TrackingService and clients. We do **not** keep the WebSocket connection inside the order DB transaction — they're separate concerns.

---

## Q5. "Surge factor changes between when the customer sees the cart and when they tap 'Place Order'. What's correct?"

**Ideal answer:**

> The surge factor must be **locked at cart creation** and not change underneath the customer.
>
> Server side: when an item is added to the cart, we read the current surge for the pickup geohash and store it on the Cart with a 10-minute expiry. All subsequent pricing reads use the locked value. If the cart sits beyond 10 min, we re-read on next interaction.
>
> Place-order request includes the cart id; the server validates the surge has not "rotted" (expired) and uses the locked factor for charge.
>
> If we don't do this, customers will (rightly) feel cheated.

---

## Q6. "How do you scale this 10× — to 100M orders/day?"

**Ideal answer:**

> A few levers:
>
> 1. **Stateless services**: scale horizontally behind LB. Already designed for this.
> 2. **DB**: from one Postgres to read replicas (read scaling), then to **sharded Postgres** by `customer_id`. We avoid Cassandra unless write rate exceeds ~50 K/sec for orders.
> 3. **Driver locations**: Redis cluster keyed by city/region. Kafka partitioned by city. No single hot key.
> 4. **Caching**: aggressive Redis caching for menus; CDN for images and menus.
> 5. **Async fan-out**: every non-critical side effect (notifications, analytics, fraud) runs from Kafka, not in the order request.
> 6. **Dispatch**: shard dispatchers per city; each owns its own driver pool.
> 7. **Partition orders by month**; archive cold partitions to S3 + Glacier.
> 8. **Multi-region** (future): each region has its own stack; cross-region only for analytics rollups.

---

## Q7. "What metrics would you put on the dashboard?"

**Ideal answer:**

> Three layers:
>
> 1. **System**: API p50/p95/p99, error rate, DB connection count, Kafka consumer lag, Redis memory.
> 2. **Domain**: orders/min by city, accept rate per restaurant, dispatch latency, driver utilization, refund rate.
> 3. **Business**: revenue, orders/DAU, AOV, cancellation rate.
>
> Plus **operational alarms**: stuck orders > 20 min, no dispatch in 5 min, refund queue depth, surge stuck > 60 min.

---

## Q8. "How do you test this end-to-end before deploying?"

**Ideal answer:**

> A pyramid:
>
> - **Unit**: domain logic (Order state transitions, PricingRule application). No IO.
> - **Repository tests**: in-memory or Testcontainers Postgres.
> - **Service tests**: stubbed integrations (PaymentClient, DispatchClient).
> - **Contract tests**: OpenAPI-driven; ensure consumers don't break.
> - **Integration tests**: full flow — place order, dispatch, deliver — in CI with Testcontainers.
> - **Load tests**: k6 or Gatling against a staging env, simulating peak lunch traffic.
> - **Chaos tests**: monthly; kill DB primary, lose Redis, slow Kafka.
>
> For deployment: blue/green or canary, gated by SLO checks. Feature flags for risky changes.

---

## Q9. "A driver's app crashes mid-delivery. What does the system do?"

**Ideal answer:**

> Drivers must ping every ~4 s. We track `last_ping_at`. A background job marks drivers `OFFLINE` if `now - last_ping_at > 60 s` and they're not in `BUSY` state.
>
> If they were in `BUSY`: we don't auto-reassign — the driver may reconnect in 30 s. We **alert support** after 2 min of no ping. Support has tools to manually reassign or contact the customer.
>
> If they reconnect, the driver app pulls their active assignment and resumes. The order itself is unchanged; we just continued tracking from the last known location, with a "tracking unavailable" UI state.

---

## Q10. "How do you avoid a 'thundering herd' when surge ends?"

**Ideal answer:**

> When surge expires, many delayed customers re-attempt orders simultaneously, spiking RPS.
>
> Mitigations:
>
> 1. **Stagger expiration** — surge ends at slightly different times per geohash (jittered).
> 2. **Rate limit per user** — soft rate limit on order placement.
> 3. **Auto-scale ahead of time** — known surge windows are pre-scaled.
> 4. **Queue with token bucket** — ack quickly, process at sustainable rate.
>
> In practice, surge usually decays smoothly (factor → 1.5 → 1.3 → 1.1) rather than cliff-edges.

---

## Q11. "Customer cancels at minute 18 of preparation. Refund?"

**Ideal answer:**

> Per our cancellation policy:
>
> - PLACED → full refund.
> - CONFIRMED within 60 s → full refund.
> - CONFIRMED after 60 s → partial refund (cancellation fee).
> - PREPARING → **no customer-initiated cancel allowed**.
>
> So this case rejects with `409 CANNOT_CANCEL_IN_STATE`. Customer must contact support for an exception. Support can override with a write to `support_actions` table, which triggers a refund (full or partial) by an admin path. We log the override.
>
> Why? The kitchen has already cooked the food. Cancelling means waste. Customer pays for our raw cost.

---

## Q12. "What if the same customer places 100 orders in 5 minutes?"

**Ideal answer:**

> Multiple defenses:
>
> 1. **Rate limit** at API gateway: 60 mutations/min per user.
> 2. **Fraud signals**: 100 orders/5 min triggers a fraud rule (Strategy in fraud engine). Suspend account, hold payments, alert.
> 3. **Payment risk score** from gateway often catches this earlier.
> 4. **Soft cap**: max 5 active in-flight orders per user.
>
> The system never silently allows pathological behavior. Each rule is independently testable.

---

## Q13. "Walk me through how you'd onboard a new payment method (e.g., Cashfree)."

**Ideal answer:**

> Pure OCP win. Our PaymentGateway is an interface:
>
> ```java
> public interface PaymentGateway {
>   Payment charge(Money amount, String idempotencyKey);
>   Refund refund(UUID paymentId, Money amount, String idempotencyKey);
>   void verifyWebhook(WebhookEvent ev, String signature);
> }
> ```
>
> Cashfree adds a new implementation. We choose between providers via a strategy + factor (e.g., per region, per A/B group). No business code changes.
>
> Operationally: dual-run for 1 week (10% Cashfree, 90% existing) to detect anomalies, then ramp.

---

## Q14. "How would you implement priority orders (e.g., paid express delivery)?"

**Ideal answer:**

> Add `Order.priority` (NORMAL / EXPRESS). Two impacts:
>
> - **Pricing**: a `PricingRule` adds an express delivery surcharge.
> - **Dispatch**: a new `ScoringStrategy` (or weight tweak) prefers express orders, and may reserve a fraction of the driver pool for express.
>
> Both changes are additive — Strategy pattern means no edits to existing code.
>
> **Risk**: starving normal orders if too many express. We monitor average pick-up latency by priority and tune.

---

## Q15. "What's the hardest part of this design and why?"

**Best answer (be honest):**

> The matching engine. Three reasons:
>
> 1. **Correctness under contention**: many dispatchers, many drivers, many orders, all racing.
> 2. **Latency**: every second of dispatch latency hurts NPS.
> 3. **Tuning**: scoring is multi-objective (distance, fairness, batch potential, rating). Real systems run thousands of A/B experiments here.
>
> A naive nearest-driver heuristic gets us 80%. Closing the last 20% is years of work.

---

## How to use this list

- Pick a question. Set a timer. Answer aloud in 90 s.
- Record yourself. Listen. Are you saying "um"? Hand-waving?
- Cycle through all 15 weekly. By interview day, you'll have the muscle memory.

A staff candidate **chooses an answer**, **states a tradeoff**, and **shows operational maturity** — every time.
