# LLD-Mastery

> A staff-engineer-grade learning repository to master Low-Level Design (LLD) and machine-coding interviews from first principles.

This repository is organized like a curriculum, not a cheatsheet. Each folder is meant to be read top-to-bottom, with code, UML, and reasoning interleaved. Every concept is explained from the ground up — what it solves, how it works, when to use it, and what to avoid.

---

## Repository Layout

```
LLD-Mastery/
├── 00_End_To_End_LLD_Tutorial/   ← The reusable framework (read first)
│
│   --- Domain-rich systems ---
├── 01_Food_Delivery_System/      ← Swiggy / DoorDash style
├── 02_Ride_Booking_System/       ← Uber / Ola style
├── 03_Hotel_Booking_System/      ← Booking.com / OYO style
├── 04_Library_Management_System/ ← Classic LLD interview
├── 05_Splitwise/                 ← Expense splitting + debt simplification
├── 06_Streak_System/             ← Engagement streak (Spotify / Duolingo style)
│
│   --- Classic / object-modeling systems ---
├── 07_Snake_and_Ladder/          ← Turn-based board game; pluggable rules
├── 08_Tic_Tac_Toe/               ← N×N board, K-in-row, AI minimax
├── 09_Vending_Machine/           ← FSM, payment, change-making
├── 10_Elevator/                  ← Multi-car scheduling (LOOK / SCAN)
├── 11_Parking_Lot/               ← Atomic spot allocation, multi-floor
│
│   --- Infra / systems-flavored ---
├── 12_Rate_Limit/                ← Token bucket / leaky bucket / sliding window
├── 13_BookMyShow/                ← Hold → Confirm → Pay; Redis SETNX + Postgres PK
├── 14_Pub_Sub/                   ← Kafka-like log; partitions, consumer groups, ISR
├── 15_LRU_Cache/                 ← HashMap+DLL, sharded, hierarchical, distributed
│
│   --- Framework / library systems ---
├── 16_Logger_Framework/          ← Hierarchical loggers, async appenders, MDC, layouts
├── 17_Feature_Flag_System/       ← LaunchDarkly-style; sticky bucketing, push updates
├── 18_Task_Scheduler/            ← Quartz / cron; FOR UPDATE SKIP LOCKED, retries, DLQ
├── 19_Circuit_Breaker/           ← Hystrix / Resilience4j; sliding window, HALF_OPEN
├── 20_Permission_Authorization_System/ ← RBAC + ABAC + ReBAC; DENY-wins, hierarchies
│
│   --- Marketplace / commerce ---
├── 21_ECommerce/                 ← Amazon-style multi-seller; place-order saga, inventory atomicity
└── 22_Car_Rental/                ← Zoomcar-style self-drive; time-slot inventory, reserve→pickup→return
```

---

## Tutorial folder (00) contents

The tutorial is the **reusable framework** for any LLD problem. Read it once, then refer back as needed.

| # | File | What you'll learn |
| - | --- | --- |
| 01 | `01_requirement_gathering.md` | Functional / non-functional / scope clarifications |
| 02 | `02_capacity_estimation.md` | DAU → RPS → storage → bandwidth math |
| 03 | `03_structured_lld_framework.md` | The 12-step playbook for any LLD problem |
| 04 | `04_solid_principles.md` | SRP, OCP, LSP, ISP, DIP — with examples |
| 05 | `05_design_patterns.md` | Strategy, State, Factory, Observer, Command, Decorator, Repository, Builder, CoR |
| 06 | `06_concurrency.md` | Race conditions, idempotency, optimistic vs pessimistic locking, sagas |
| 07 | `07_api_design.md` | REST, idempotency keys, error modeling, pagination, versioning, rate limits |
| 08 | `08_database_design.md` | ER, indexes, locking, audit, partitioning, sharding, caching, migrations |
| 09 | `09_uml_tutorial.md` | Class, sequence, state, package, component diagrams |
| 10 | `10_lld_interview_framework.md` | How to think and communicate in an LLD interview |
| 11 | `11_machine_coding_framework.md` | Checklists, templates, reusable blueprint |

---

## System deep-dive structure (folders 01–20)

Each system folder follows the **same 14-file structure**:

| File | Purpose |
| --- | --- |
| `01_requirements.md` | FR / NFR / clarifications / scope |
| `02_capacity_estimation.md` | QPS, storage, traffic |
| `03_hld.md` | High level architecture |
| `04_domain_model.md` | Entities, aggregates, invariants |
| `05_database_design.md` | Schema, indexes, locking |
| `06_api_design.md` | REST endpoints, idempotency |
| `07_class_diagrams.md` | UML class diagrams |
| `08_sequence_diagrams.md` | Lifecycle sequence diagrams |
| `09_state_machines.md` | Order/Ride/Booking/Loan lifecycle |
| `10_design_patterns.md` | Patterns used and why |
| `11_concurrency_and_scaling.md` | Locking, races, scale |
| `12_machine_coding_skeleton/` | Production-grade Java skeleton |
| `13_extensions_and_tradeoffs.md` | Evolution and alternatives |
| `14_interviewer_followups.md` | Staff-level grilling Q&A |

---

## What each system teaches you

| System | Headline concepts |
| --- | --- |
| **Food Delivery** | Order state machine, dispatch algorithm, geospatial indexing, surge pricing, batching, inventory locking, outbox pattern |
| **Ride Booking** | Matching engine, surge per zone, ride lifecycle, geohash / S2, driver state machine, SOS / safety |
| **Hotel Booking** | Calendar inventory model, double-booking prevention, dynamic pricing, state pattern for bookings, cancellation policies |
| **Library** | Book vs Copy modeling, reservation queues, fine strategies, borrow concurrency, multi-branch transfers |
| **Splitwise** | Split algorithms (equal/exact/percent/share/itemwise), debt simplification graph, settlement engine, multi-currency, balance from event log |
| **Streak System** | Day-basis streak math, user-TZ calendar arithmetic, layered idempotency, admin-configurable activity classifier, milestone engine, anti-cheat against late events |
| **Snake & Ladder** | Turn-based engine, pluggable dice / rules, snakes/ladders as decorators, observer for events |
| **Tic Tac Toe** | N×N board generalization, K-in-row win check (incremental O(K)), pluggable players (human / random / minimax) |
| **Vending Machine** | Finite state machine (Strategy + State pattern), inventory atomicity, change-making (greedy + DP), payment adapter |
| **Elevator** | Multi-car scheduling, LOOK/SCAN algorithms, floor request queues, optimization for energy / waiting time |
| **Parking Lot** | Multi-floor / multi-vehicle inventory, atomic spot allocation under contention, ticket lifecycle, billing strategies |
| **Rate Limit** | Token bucket, leaky bucket, fixed window, sliding window log, sliding window counter; Redis Lua for atomic ops; per-user/IP/API limits |
| **BookMyShow** | Movie/Theatre/Show/Seat modeling, Hold (Redis SETNX) → Confirm (Postgres PK), idempotent payments, surge pricing locked at hold time |
| **Pub/Sub (Kafka-like)** | Append-only partition log, consumer groups & offsets, replication & ISR, idempotent producer, segmented files + sparse index |
| **LRU Cache** | HashMap+DLL O(1), pluggable eviction policy (LRU/LFU/W-TinyLFU), TTL, single-flight loader, sharded for concurrency, hierarchical L1+L2 |
| **Logger Framework** | Hierarchical loggers, parameterized lazy formatting, MDC ThreadLocal, pluggable Appenders/Layouts/Filters, async ring buffer, hot-reload config |
| **Feature Flag System** | Local SDK eval (<1ms), targeting rules, sticky percentage bucketing via hash(flag+userId), prerequisites, push updates via SSE, audit log |
| **Task Scheduler** | DelayQueue / min-heap (in-process), `FOR UPDATE SKIP LOCKED` (distributed), Trigger as pure function, retry+backoff, visibility timeout, DLQ |
| **Circuit Breaker** | 3-state machine (CLOSED/OPEN/HALF_OPEN), sliding window (count/time), failure & slow-call rates, CAS transitions, Bulkhead → CB → Retry → Timeout |
| **Permission System** | RBAC + role hierarchy + resource hierarchy, DENY-wins evaluator, default deny, cached effective perms with pub/sub invalidation, ABAC / ReBAC layers |
| **E-Commerce Marketplace** | 3-level catalog (Product → Variant → SellerOffer), atomic inventory triple-counter, place-order saga (reserve → authorize → persist → capture → commit), 2-phase payment, multi-seller sub-orders, outbox + reconciliation, browse-plane vs buy-plane split |
| **Car Rental** | Time-slot inventory (atomic per-hour bucket), VehicleModel vs Vehicle (logical vs physical), reserve→pickup→return saga, geofenced unlock with multi-source GPS, composite pricing (base locked + components at return), MIT for delayed damage charges, tiered cancellation Strategy |

---

## Reading Order

1. **Start with `00_End_To_End_LLD_Tutorial/`** — it teaches the framework you will reuse on every system.
2. Pick a system and read the files in numerical order. Each builds on the previous.
3. Build the machine-coding skeleton yourself before reading the reference skeleton.
4. End with `14_interviewer_followups.md` — answer them out loud as if you were in an interview.

---

## How to Use This for Interview Prep

| Phase | Time | What to do |
| --- | --- | --- |
| Foundations | 1 week | Read `00_End_To_End_LLD_Tutorial/` end-to-end |
| Per-system deep dive | 2 days each | Read all 14 files; redo the design on a whiteboard |
| Machine coding | 90 min each | Implement the skeleton without looking at the reference |
| Mock interviews | weekly | Use `14_interviewer_followups.md` as the interviewer's script |

A complete loop: tutorial → 22 systems × 14 files → 22 skeletons → 340+ follow-up questions answered out loud.

---

## Pattern map across systems

Note how the **same patterns recur** across very different systems. Master them once, apply them everywhere.

### Domain-rich systems

| Pattern | Food Delivery | Ride Booking | Hotel | Library | Splitwise | Streak |
| --- | --- | --- | --- | --- | --- | --- |
| Strategy | Pricing rules, scoring | Pricing, scoring, surge algo | Pricing, cancellation policy | Fine calculator, borrow policy | Split methods | Activity classifier |
| State pattern | Order lifecycle | Ride lifecycle | Booking lifecycle | (light, enum-driven) | (light) | Empty / Active |
| Optimistic CAS | Order, Driver, Assignment | Ride, Driver | Booking, Inventory | Copy, Member | Expense | StreakState, AdminConfig |
| Idempotency keys | Place order, payments | Booking, payments | Booking, payments | Borrow, fine pay | Expense, settlement | (user, type, day) + Idempotency-Key |
| Outbox pattern | OrderPlaced events | RideRequested events | BookingConfirmed events | LoanIssued events | ExpenseCreated events | StreakAdvanced events |
| Repository | All aggregates | All aggregates | All aggregates | All aggregates | All aggregates | All aggregates |
| Chain of Responsibility | Validators | Validators | Validators | Validators | Validators | (n/a) |
| Factory | Pricing rules, gateway | Pricing, scoring | Policy | Policy, calculator | Split strategy | Classifier factory (hot reload) |
| Observer/Pub-Sub | Order events → fanout | Ride events → fanout | Booking events → fanout | Loan events → fanout | Expense events → fanout | Streak events → milestone |
| Command | Use cases as records | Same | Same | Same | Same | Same |

### Classic / object-modeling systems

| Pattern | Snake & Ladder | Tic Tac Toe | Vending Machine | Elevator | Parking Lot |
| --- | --- | --- | --- | --- | --- |
| Strategy | Dice (deterministic / random / loaded), Move rules | Player (Human / Random / Minimax) | Payment processor, Change-making (Greedy / DP) | Scheduling (FCFS / LOOK / SCAN) | Spot allocator, Pricing |
| State pattern | (light) | (light) | Idle / HasMoney / Dispensing — explicit FSM | Door / Motion FSM | Spot AVAILABLE / OCCUPIED / RESERVED |
| Decorator | Snakes & ladders modify board | n/a | n/a | n/a | n/a |
| Observer | GameEventListener (move, win) | GameEventListener | DisplayListener | FloorIndicator | Gate / Display |
| Atomic CAS | n/a (single thread) | n/a | Inventory atomic decrement | Per-car single-writer | `PRIMARY KEY (lot, spot)` analog |

### Infra / systems-flavored

| Pattern | Rate Limit | BookMyShow | Pub/Sub | LRU Cache |
| --- | --- | --- | --- | --- |
| Strategy | Algorithm (Token / Leaky / Fixed / Sliding-log / Sliding-counter) | Pricing, SeatLock | Partitioner, Retention policy | EvictionPolicy (LRU / LFU / W-TinyLFU) |
| Builder | RateLimiterBuilder | n/a | (KafkaProducer.builder) | CacheBuilder |
| Decorator | Per-API / per-user / per-IP layered limiters | n/a | n/a | Stats / WriteThrough / RefreshAhead |
| Composite | Multi-layer limiter | n/a | n/a | Sharded + Hierarchical caches |
| Atomic CAS | Redis Lua script for token decrement | Redis SETNX hold + Postgres PK confirm | Per-partition single-writer + ISR | Per-shard lock; ConcurrentHashMap CAS |
| Outbox | n/a | Booking confirm + outbox in same TX | The whole point: replicated log | n/a |
| Single-flight | n/a | n/a | n/a | getOrLoad → only one loader runs |
| Versioned cache | n/a | layout:show:{id}:v{version} | n/a | Versioned keys for invalidation |

### Framework / library systems

| Pattern | Logger | Feature Flag | Task Scheduler | Circuit Breaker | Permission |
| --- | --- | --- | --- | --- | --- |
| Strategy | Appender / Layout / Filter | Operator / Bucketing / Subscriber | Trigger / Backoff / JobStore | SlidingWindow / ExceptionClassifier | AuthorizationStore / PolicyEvaluator |
| Decorator | AsyncAppender wraps any Appender | n/a | n/a | Decorators chain (Bulkhead → CB → Retry → Timeout) | n/a |
| Composite | Logger hierarchy (parents+additive) | Rule = ANDed Conditions | n/a | n/a | Role hierarchy + Resource hierarchy |
| Observer | EventListener for state changes | FlagStore listeners; SSE stream | DLQ events; misfire events | EventListener (state, calls) | Audit log + cache invalidation events |
| State pattern | Appender lifecycle (CREATED / STARTED / ERROR) | Subscriber lifecycle | Job FSM (SCHEDULED / CLAIMED / RUNNING / DLQ) | CLOSED / OPEN / HALF_OPEN with CAS transitions | Decision FSM (SCANNING / DENIED / ALLOWED) |
| Pure function | n/a | Evaluator(flag, ctx) → variation | Trigger.nextFireTime(prev) | n/a | PolicyEvaluator.decide(...) |
| Cache-aside | n/a | Local SDK config; CDN snapshot | n/a | n/a | userPerms cache + ancestors cache |
| Pub/Sub | n/a | Kafka updates → SSE → SDK | LISTEN/NOTIFY (V2) | n/a | Cache invalidation events |
| Atomic CAS | Volatile state + recursive guard | Optimistic If-Match on admin writes | FOR UPDATE SKIP LOCKED for claims | AtomicReference\<State\> for transitions | Optimistic version on role edits |
| Memento | LogEvent captures MDC snapshot | n/a | n/a | (light) | n/a |
| Default-deny / fail-closed | Listener errors swallowed | Default value on error | DLQ for poison jobs | DENY when REJECTED | Default deny + DENY > ALLOW |

### Marketplace / commerce

| Pattern | E-Commerce | Car Rental |
| --- | --- | --- |
| Saga (orchestrator) | place-order saga | place-reservation saga |
| Strategy | BuyBoxResolver, PaymentGateway, PricingPolicy | CancellationPolicy, IoTAdapter, PricingComponent, BuyBoxAlloc |
| Composite | n/a | CompositePricing of components |
| Aggregate root (DDD) | Product, SellerOffer, Inventory, Cart, Order | Reservation, Trip, Vehicle, DamageClaim |
| Outbox | OrderDB → Kafka via outbox table | ReservationDB → Kafka via outbox table |
| Idempotency key | Place-order, payment, webhooks | Place-reservation, pickup, payment, MIT damage charge |
| Two-phase commit (light) | Payment AUTH → CAPTURE | AUTH at booking → CAPTURE at return → MIT later |
| Atomic CAS | `UPDATE inventory ... WHERE available >= qty` | PK conflict on `(vehicle_id, hour_bucket)` |
| Specification | Coupon eligibility | KYC eligibility, drop-zone validation |
| Sealed types / ADT | n/a | `ReserveResult = Reserved \| Conflict` |
| Observer / Pub-Sub | Order events → fulfillment, notifications | Reservation/Trip events → notification, ops |
| Circuit Breaker / Bulkhead | Payment gateway calls | Payment gateway + IoT modem |
| State pattern (light) | Order, SubOrder, Payment, Reservation, Return | Reservation, Trip, Payment, Vehicle, DamageClaim |

---

## Concurrency map

The single most-tested topic in LLD interviews:

| System | Critical race | Solution |
| --- | --- | --- |
| Food Delivery | Two orders for last item | DB CAS atomic UPDATE |
| Food Delivery | Two dispatchers pick same driver | CAS + `SELECT FOR UPDATE SKIP LOCKED` |
| Ride Booking | Two riders, one driver | Driver state CAS |
| Hotel Booking | Two guests, last room | Per-night atomic UPDATE |
| Library | Two members, one copy | Copy state CAS |
| Library | Reservation queue head | `SELECT FOR UPDATE SKIP LOCKED` |
| Splitwise | Concurrent expense edits | Optimistic version |
| Splitwise | Concurrent balance updates | Single-writer per Kafka partition |
| Streak | Multi-device same-day events | Redis SETNX dedup + DB CAS |
| Streak | Late / offline events grow streak (cheat) | Domain returns `Backfilled` — no streak math |
| Streak | Admin switches active type mid-read | Cache version-prefix bump |
| Vending Machine | Two buyers, last item | Atomic decrement / CAS on inventory |
| Elevator | Two requests assigned to same car | Single-writer scheduler per car |
| Parking Lot | Two vehicles, last spot | Atomic ticket-creation with PK on (lot, spot) |
| Rate Limit | Concurrent token decrements | Redis Lua script atomic check-and-decrement |
| BookMyShow | Two users, same seat | Redis SETNX (hold) + Postgres `PRIMARY KEY (show, seat)` (confirm) |
| Pub/Sub | Two writers per partition | Single-leader-writer per partition; ISR-based commit |
| LRU Cache | Concurrent get/put across threads | Sharded locks (default); Caffeine-style ring buffer (advanced) |
| LRU Cache | Cache stampede on hot key miss | Single-flight loader (per-key lock or `CompletableFuture`) |
| Logger Framework | Many threads write the same logger | Volatile effective level; `ConcurrentHashMap` registry; immutable LogEvent |
| Logger Framework | Recursive logging from a custom appender | `ThreadLocal<Boolean>` reentrant guard |
| Logger Framework | Async queue full burst | Per-appender BLOCK / DROP_NEWEST / DROP_OLDEST policy |
| Feature Flag | Two admins editing the same flag | Optimistic concurrency via `If-Match: version` |
| Feature Flag | Stable rollout across servers | `hash(flag.key + ":" + userId) mod 10000` — deterministic |
| Feature Flag | SDK fleet bootstrap storm after deploy | CDN-cached snapshot + jittered SSE reconnect |
| Task Scheduler | Two workers claim the same job | `SELECT … FOR UPDATE SKIP LOCKED` |
| Task Scheduler | Worker crashes mid-execution | Visibility timeout + lease + heartbeat; idempotency |
| Task Scheduler | Lease expiry race (slow task vs new claim) | Ownership-guarded `UPDATE … WHERE claimed_by = $me` |
| Circuit Breaker | Many threads simultaneously trip | `AtomicReference<State>` + CAS — exactly-one transition |
| Circuit Breaker | HALF_OPEN probe count | Non-fair `Semaphore(N)` for permitted probes |
| Circuit Breaker | Listener throws on event | Catch and swallow; never break the breaker |
| Permission | Stale permissions after revoke | Pub/Sub invalidation across cache instances + short TTL |
| Permission | Cache stampede on TTL expiry | Single-flight per user + jittered TTL |
| Permission | Default behavior on missing rule | **Default deny** (fail-closed) |
| E-Commerce | Two buyers, last unit | Atomic conditional UPDATE: `available = available - qty WHERE available >= qty` |
| E-Commerce | Hot SKU contention on flash sale | Sharded inventory counters per (offer, warehouse) |
| E-Commerce | Place-order double-click | `UNIQUE(user_id, idempotency_key)` on orders |
| E-Commerce | Payment auth ok but capture fails | Order is durable before capture; reconciler retries; void on max retries |
| E-Commerce | Reservation TTL expires during checkout | `UPDATE reservations SET status='COMMITTED' WHERE id=? AND status='ACTIVE'` |
| E-Commerce | Webhook replays from gateway / carrier | `INSERT INTO processed_events ON CONFLICT DO NOTHING` |
| Car Rental | Two renters, same vehicle, overlapping windows | PK `(vehicle_id, hour_bucket)` natural mutex; INSERT ON CONFLICT DO NOTHING per slot |
| Car Rental | Place-reservation double-click | `UNIQUE(user_id, idempotency_key)` on reservations |
| Car Rental | Pickup geofence spoofing | Multi-source location (GPS + cell + Wi-Fi) + velocity sanity + device attestation |
| Car Rental | Mid-trip extension racing next reservation | Treat extension as new mini-reservation; PK conflict refuses |
| Car Rental | Reservation TTL race during checkout | Status-guarded `UPDATE WHERE status='HELD'`; sweeper uses same predicate |
| Car Rental | Damage charge weeks after trip | MIT against saved card with `claim_id` as gateway idempotency key |
| Car Rental | Capture failure on return | Reservation+Trip durable before capture; reconciliation retries; void on max retries |

---

## Quality Bar

Every concept introduced — from optimistic locking, to geohash, to debt simplification, to the Strategy pattern — is explained:

- **What** problem it solves
- **How** it works internally
- **Where** we use it in this design
- **Tradeoffs** vs alternatives
- **Implementation** in code

Nothing is hand-waved. If something is, it is a bug — open an issue and fix it.

---

## A note on code

The Java skeletons are **theoretical** demonstrations. They use in-memory repositories and reflection-based mutation in places to keep the LLD focus on **structure and concurrency primitives**, not framework wiring. Production code would substitute JPA repositories, Kafka producers, real DB adapters, etc.

Compile-and-run is intentionally not the primary goal. The goal is to internalize the **shape** of the design — the responsibilities of each class, the boundaries between layers, the way patterns compose.

Treat each skeleton as a wireframe. Implement the actual machine-coding round yourself.

---

## Final word

Staff-level LLD is not about memorizing patterns. It's about choosing the right primitive for each problem, knowing the tradeoffs, and being able to defend your choice in 90 seconds.

This repo is the path. Walk it slowly.
