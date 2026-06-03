# 10 · LLD Interview Framework — How to Drive a 60-Minute Round

> The LLD interview is not a quiz. It's a **collaborative design session.** Your job is to drive it like a senior architect leading a design review.

---

## What the Interviewer Is Actually Evaluating

Most candidates think interviewers grade their **design**. They don't.

Interviewers grade your **process and reasoning**:

| What they grade | Weight |
| --- | --- |
| Did you clarify scope? | 15% |
| Did you propose a coherent design? | 20% |
| Did you justify each choice? | 20% |
| Did you discuss tradeoffs / alternatives? | 20% |
| Did you handle follow-ups well (concurrency, scale, evolution)? | 20% |
| Code skeleton quality (if asked) | 5% |

Note: **40% is reasoning + tradeoffs.** That's where most candidates lose.

---

## The 60-Minute Game Plan

```
0:00–0:05    Listen to the prompt. Repeat back.
0:05–0:10    Clarify scope. Write FR / NFR / out-of-scope on the board.
0:10–0:13    Capacity estimation (5 numbers).
0:13–0:18    Identify actors and core entities.
0:18–0:25    APIs + DB schema sketch.
0:25–0:38    Class design + design patterns.
0:38–0:45    Sequence + state diagrams.
0:45–0:55    Concurrency, scale, failure modes.
0:55–0:60    Tradeoffs, extensions, what you'd build next.
```

If you fall behind, **cut depth, not steps.** A complete design at 70% depth scores higher than 50% of a perfect design.

---

## How to Drive

### Speak in Layers, Not Words

Bad:
> "I'd have an Order class with status, items, customer..."

Good:
> "I'll model **three aggregates**: Order, Restaurant, Driver.
> The Order aggregate owns OrderItems and a snapshot of pricing/address.
> The Restaurant and Driver are referenced by ID, not by composition.
> This gives us per-aggregate consistency boundaries."

The second version teaches the listener your **mental model**. The first lists fields.

### Always Justify

After every design choice, add **"because..."**:

- "I'll use Postgres **because** we need strong consistency for orders."
- "I'll use optimistic locking **because** conflict rate is low."
- "I'll use the State pattern **because** behavior depends heavily on status."

If you can't articulate "because", you're guessing. Stop and think.

### Always Compare

After every choice, mention what you considered and rejected:

- "Cassandra **was an option** but we don't need its write throughput, and joins matter."
- "Pessimistic locking **would also work** but optimistic is cheaper given low contention."
- "Inheritance **was the alternative** but it would violate LSP."

Two alternatives + reasoning beat one assertion every time.

---

## What to Say Out Loud

### When stuck

> "Let me think about that for a second... I see two approaches: A and B. Let me weigh them."

Silence is fine. Silence with **a stated direction** is even better.

### When changing your mind

> "Earlier I picked X. Now that we're in concurrency I see X has a race; let me switch to Y."

This shows iterative thinking. Don't pretend you didn't say X.

### When asked "why?"

> "Three reasons: ..."

Three is better than one. Two is fine. One looks shallow.

### When you don't know

> "I don't know offhand. My guess is X because Y. Want me to assume that and proceed?"

Honesty + direction. Never bluff.

---

## The 7 Most Common Follow-Ups (and How to Win Them)

### 1. "What if the DB goes down?"

**Win answer:** "Read replicas continue serving reads. Writes degrade. The app should fail-open or fail-closed depending on criticality. For orders, fail-closed: return 503 with retry-after, queue retry on the client. For browsing menus, fail-open: serve from cache."

### 2. "What about concurrency?"

Refer to [`06_concurrency.md`](./06_concurrency.md). Walk through:
- Idempotency at API
- Optimistic lock at row
- DB CAS for inventory
- Saga / Outbox for cross-service

### 3. "How would you scale this 10x?"

Lead the discussion through:
- Horizontal scale stateless services.
- Read replicas + cache.
- Partition or shard the hot table.
- Move heavy work async (queue).
- Move read-heavy aggregates to a denormalized store.

### 4. "How do you handle bad data / data corruption?"

- DB constraints catch most.
- App-level validations catch business rules.
- Audit log for every mutation.
- Reconciliation jobs for invariants (e.g., balance ≥ 0).

### 5. "How do you migrate the schema?"

- Zero-downtime migrations: add → backfill → swap → drop.
- Each step deployed and verified independently.

### 6. "What metrics would you monitor?"

| Layer | Metrics |
| --- | --- |
| API | RPS, p50/p95/p99 latency, 4xx/5xx rate |
| DB | Query latency, connections, replication lag |
| Domain | Orders/min, order success rate, dispatch latency |
| Business | Revenue, MAU, conversion |

### 7. "How do you test this?"

- Unit tests for domain logic (no IO).
- Repository tests against an in-memory DB or testcontainers.
- Service tests with stubbed dependencies.
- Integration tests for happy path.
- Contract tests for APIs.
- Load tests for hot endpoints.
- Chaos tests for failure modes.

---

## Whiteboard Layout

Don't write randomly. Use a fixed layout:

```
┌────────────────────────────────────────────────────────┐
│ TITLE: Design Food Delivery                            │
├────────────────────────────────────────────────────────┤
│ ACTORS    │  CORE FR             │  NFR / NUMBERS      │
│ Customer  │  Place order         │  p99 < 200ms        │
│ Driver    │  Track order         │  200 RPS peak       │
│ Restaurant│  Dispatch            │  9M orders/day      │
├────────────────────────────────────────────────────────┤
│ ENTITIES                                               │
│ Order, OrderItem, Customer, Restaurant, Driver         │
├────────────────────────────────────────────────────────┤
│ APIs                                                   │
│ POST /orders, GET /orders/{id}, POST /orders/{id}:cancel│
├────────────────────────────────────────────────────────┤
│ DB                                                     │
│ orders(id, customer_id, status, total, version, ...)   │
│ idx (customer_id, created_at), idx active partial      │
├────────────────────────────────────────────────────────┤
│ CLASSES                                                │
│ OrderService → OrderRepository                         │
│ DispatchService → DispatchStrategy                     │
├────────────────────────────────────────────────────────┤
│ SEQUENCE / STATE                                       │
│ ...                                                    │
└────────────────────────────────────────────────────────┘
```

The interviewer can then see at a glance where you've been thorough and where you've been thin.

---

## Don't-Do List

- Don't start coding before sketching.
- Don't dive into algorithms before agreeing on the model.
- Don't quote unrelated patterns ("I'd use Singleton") without trigger.
- Don't claim "infinitely scalable" — quantify.
- Don't argue. If interviewer pushes, **engage with their concern** before defending.
- Don't ignore failure modes — they will ask.
- Don't run out of time with no concurrency discussion.

---

## How to Recover from a Bad Start

If you're 15 min in and feel lost:

1. **Stop.** Take a breath.
2. **Restate the prompt.** "Let me make sure I'm solving the right problem: we want X for users Y, optimizing Z."
3. **Re-scope down.** "Let me focus on the core path A and we can extend if time permits."
4. **Pick one entity** and go deep.

The interviewer would much rather you reset and produce one solid sub-design than flail across the whole system.

---

## What Staff-Level Looks Like

A senior delivers a working design. **Staff** delivers:

- A working design + alternatives considered + reasoning.
- Awareness of failure modes and operational concerns.
- A view of the system's evolution over the next year.
- The ability to **negotiate** scope and depth based on time.
- Calm, structured communication under uncertainty.
- Specific numbers, not vibes.

When the interviewer asks "anything to add?", a great closing answer is:

> "I'd build this in three phases:
> 1. Phase 1 covers the happy path and core APIs (~6 weeks).
> 2. Phase 2 adds concurrency hardening, idempotency, observability (~4 weeks).
> 3. Phase 3 adds the extensions: surge pricing, pool rides, multi-region (~2 quarters).
> The design supports all three without rework."

This shows you think like an engineering lead, not just a coder.

---

## Mock Interview Drill

Use the `14_interviewer_followups.md` file in each system folder. Practice answering each question **out loud, in 2–3 minutes**, under pressure.

Record yourself. Listen. You will hear your filler words and your hand-waving. Iterate.

---

## Checklist

- [ ] I clarified scope before designing.
- [ ] I stated 5 capacity numbers.
- [ ] I identified aggregates, not just entities.
- [ ] I named at least 3 design patterns with triggers.
- [ ] I drew at least one sequence and one state diagram.
- [ ] I identified at least 3 concurrency hazards and how I handle each.
- [ ] I discussed alternatives I rejected, with reasons.
- [ ] I left 5 minutes for evolution / extensions.
