# 00 · End-to-End LLD Tutorial

> The reusable framework you will apply to every system in this repository.

This tutorial teaches you **how to think**, not **what to memorize**. After working through it once, every LLD problem reduces to applying the same 12-step playbook.

---

## Sections

| # | File | Why it matters |
| - | --- | --- |
| 1 | [01_requirement_gathering.md](./01_requirement_gathering.md) | 50% of LLD interviews are won here |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) | Sizes the design — drives DB choice, sharding, caching |
| 3 | [03_structured_lld_framework.md](./03_structured_lld_framework.md) | The 12-step playbook for any problem |
| 4 | [04_solid_principles.md](./04_solid_principles.md) | Why most "bad" code is just SOLID violations |
| 5 | [05_design_patterns.md](./05_design_patterns.md) | 9 patterns you'll reach for in 90% of LLDs |
| 6 | [06_concurrency.md](./06_concurrency.md) | Locks, CAS, idempotency, races — the hardest topic |
| 7 | [07_api_design.md](./07_api_design.md) | REST, idempotency keys, pagination, errors |
| 8 | [08_database_design.md](./08_database_design.md) | Schema, indexes, partitioning, locking |
| 9 | [09_uml_tutorial.md](./09_uml_tutorial.md) | Class / sequence / state / package / component |
| 10 | [10_lld_interview_framework.md](./10_lld_interview_framework.md) | How to drive a 60-min interview |
| 11 | [11_machine_coding_framework.md](./11_machine_coding_framework.md) | 90-min coding round template |

---

## The 12-Step Playbook (preview)

```
1.  Clarify scope          (5 min)   ← FR, NFR, what's IN, what's OUT
2.  Capacity estimation    (3 min)   ← only if NFR-driven
3.  Identify actors        (2 min)   ← who calls the system
4.  Identify core entities (5 min)   ← User, Order, Driver, etc.
5.  Identify aggregates    (3 min)   ← consistency boundaries
6.  Define APIs            (5 min)   ← contract first
7.  DB schema              (5 min)   ← tables, indexes, FKs
8.  Class design           (10 min)  ← interfaces, abstract, concrete
9.  Pick patterns          (5 min)   ← Strategy / State / Factory / etc.
10. Sequence diagrams      (5 min)   ← happy path + 1-2 edge cases
11. State machines         (5 min)   ← if entity has lifecycle
12. Concurrency + scale    (7 min)   ← locks, races, idempotency
```

**Total: 60 minutes.** This is the LLD round. You can finish in 45 with practice.

---

## Mental Models You'll Use Constantly

1. **Aggregates** — the unit of consistency. Lock at this boundary.
2. **State machines** — most "real" entities have them (Order, Ride, Booking).
3. **Strategy + Factory** — 80% of "what pattern should I use?" answers.
4. **Idempotency keys** — every external mutation API needs one.
5. **Optimistic locking** — the default choice, not pessimistic.
6. **Repository pattern** — abstracts persistence away from domain.
7. **Domain events** — decouple side effects from core flows.

Each is explained in detail in the sections above.
