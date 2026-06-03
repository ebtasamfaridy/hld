# 02 · Capacity Estimation

> "Numbers are how senior engineers separate a believable design from hand-waving."

---

## When Does LLD Need Capacity Estimation?

**Always do at least back-of-envelope numbers, even in LLD.** They drive:

- Index choice (in-memory vs disk)
- DB partitioning vs single-node
- Cache sizing
- Async vs sync flows
- Whether a queue is needed
- Whether you need a search engine

You can skip it only if the interviewer says "assume small scale, focus on design." Otherwise, **5 minutes** of estimation buys you 50 minutes of credibility.

---

## The 5-Number Model

For any system, estimate these 5 numbers:

| # | Number | How to derive |
| - | ------ | ------------- |
| 1 | **DAU** (daily active users) | Given or inferred from total users (10–20%) |
| 2 | **Read RPS / Write RPS** | DAU × actions/day ÷ 86,400, with peak factor 2–4× |
| 3 | **Storage / day** | Writes/day × bytes per record |
| 4 | **Storage / 5 years** | Storage/day × 365 × 5, with growth factor |
| 5 | **Bandwidth** | RPS × payload size |

Memorize the conversions:

```
86,400  seconds in a day      ≈ 10^5
1 KB    ≈ 10^3 bytes
1 MB    ≈ 10^6 bytes
1 GB    ≈ 10^9 bytes
1 TB    ≈ 10^12 bytes
```

Use **powers of 10** mentally — round aggressively.

---

## Worked Example 1: Food Delivery

**Given:** Swiggy India

```
Total users:        200 M
DAU:                30 M       (15% of total)
Orders / DAU:       0.3        (1 order every ~3 days per active user)
Orders / day:       9 M        (30M × 0.3)
Avg order value:    ~₹400
```

### Write QPS

```
9 M / 86,400 ≈ 100 RPS  (average)
Peak factor 4×        ≈ 400 RPS  (lunch + dinner)
```

But each order also produces:
- 1 order record
- ~3 status updates (PLACED → CONFIRMED → DISPATCHED → DELIVERED)
- 1 driver location stream during delivery (~30 minutes × 5 Hz = 9000 points)

```
Driver location writes/day:  9 M × 9000 = 8.1 × 10^10
Per second:                  ~10^6 ≈ 1 M RPS
```

That single number tells you: **driver location stream cannot go to your main RDBMS.** It needs a stream / Redis / Kafka pipeline. **This is the kind of insight estimation gives you.**

### Read QPS

Each user opens app → restaurant list → menu → order → tracking
Average reads/order ≈ 50 (pagination, refreshes)

```
Reads/day:   9 M × 50 = 4.5 × 10^8
Peak RPS:    ~20,000
```

Drives: heavy read caching, CDN for menus, regional read replicas.

### Storage

Per order ≈ 2 KB (JSON metadata + items + audit)
```
Storage/day:    9 M × 2 KB ≈ 18 GB
Storage/5 yr:   18 GB × 365 × 5 × 1.5 (growth) ≈ 50 TB
```

50 TB exceeds single-node Postgres. **You need partitioning.**

---

## Worked Example 2: Library Management System

**Given:** University library, 50,000 students, 10,000 books × 3 copies = 30,000 copies.

```
DAU:          5,000 students  (10%)
Borrows/day:  ~500
Borrows/sec:  0.006           ← negligible
Storage:      30K copies × 100 B + 50K users × 200 B ≈ 13 MB
```

**Conclusion:** No sharding needed. Single Postgres node fits everything. Don't over-engineer.

This shows: **estimation also helps you avoid over-engineering.** A library system does not need Cassandra.

---

## Worked Example 3: Splitwise

```
Users:           100 M
DAU:             10 M
Expenses/day:    1 M           (0.1 expenses/DAU)
Group members:   avg 5
Settlements/day: 100 K         (10× less than expenses)
```

### Storage

Per expense:
- 1 expense row (~500 B)
- N split rows (~5 × 200 B = 1 KB)
- 1 audit row (~300 B)
- Total ≈ 2 KB

```
Storage/day:   1 M × 2 KB = 2 GB/day
Storage/5 yr:  2 GB × 365 × 5 ≈ 3.65 TB
```

Fits comfortably with vertical partitioning + monthly archival.

### Balance computation

If we recompute net balance per user-pair per request:
- Each user has avg ~10 active relationships
- Compute = sum of all expenses + settlements between them
- Average relationship has 50 expenses → ~50 rows to scan/read

**Insight:** You probably want a **materialized balance table** (`user_balances(user_a, user_b, amount)`), updated transactionally with each expense. We discuss this in `05_database_design.md` of `05_Splitwise/`.

---

## Read/Write Ratios

```
Twitter feed:         100 : 1
Food delivery menus:   50 : 1
Splitwise:              5 : 1
Banking ledger:         2 : 1
Driver location:    1 : 100   (writes >> reads, for 99% of stream)
```

This ratio decides:
- **Read-heavy** → cache aggressively, use CQRS, search index
- **Write-heavy** → batch, use append-only logs, partition by writer
- **Balanced** → standard RDBMS works

---

## Peak vs Average

Always multiply average by a **peak factor**:

| System | Peak factor | Why |
| --- | --- | --- |
| Food delivery | 3–5× | Lunch (12–2 PM), dinner (7–10 PM) |
| Ride hailing | 4–6× | Office hours, airport peaks |
| Hotel booking | 2–3× | Weekends, holidays |
| News feed | 2× | Mornings/evenings |
| Tax filing | 100× | Last week before deadline |

If you say "200 RPS average," follow with **"so we design for ~800 RPS peak."**

---

## Memory & Cache Sizing

### Hot data ratio

Most systems have an 80/20 distribution: **20% of data serves 80% of reads.**

For Swiggy: 20% of restaurants × 20% of menu items get 80% of traffic.

```
Hot menu data: ~100 K restaurants × 50 items × 1 KB ≈ 5 GB
```

A 16 GB Redis cluster easily holds this with room to grow.

### Hit ratio target

- **>95%**: cache is doing its job; reads stay fast.
- **80–95%**: increase TTL or pre-warm cache.
- **<80%**: cache is wrong size or wrong key; redesign.

---

## Bandwidth Estimation

```
Bandwidth = RPS × payload size
```

For Swiggy menu API:
```
Reads/sec: 20,000
Payload:    50 KB (JSON menu)
Bandwidth: 20,000 × 50 KB = 1 GB/sec    ← per region
```

That number tells you: **you must compress**, **you must CDN**, and **you must design pagination**.

---

## When NOT to Spend Time on Estimation

- Library/Parking lot/Vending machine LLDs — single-node, ignore.
- "Design Tic-Tac-Toe" — clearly unnecessary.
- When the interviewer says "skip scale, focus on classes."

But mention it briefly: "At this scale (~1 game/sec), we don't need sharding. Single node is fine."

---

## Cheat Sheet of Useful Constants

```
1 KB packet over network ≈ 0.1 ms (intra-DC) | ~10 ms (cross-region)
SSD random read           ≈ 0.1 ms
HDD random read           ≈  10 ms
RAM access                ≈ 0.0001 ms
DB indexed read           ≈ 1–10 ms
DB write (with sync WAL)  ≈ 5–20 ms
Cache (Redis) read        ≈ 0.5 ms

1 modern server can handle:
  - 10–50 K RPS for stateless service
  - 5–20 K writes/sec for Postgres
  - 100 K writes/sec for Cassandra
  - 1 M ops/sec for Redis
```

These let you sanity-check any RPS/storage claim.

---

## Output of This Step

After estimation you should have **5 numbers on the whiteboard**:

```
DAU:          30 M
Write RPS:    400 (peak)
Read RPS:     20 K (peak)
Storage/5y:   50 TB
Bandwidth:    ~1 GB/sec read, ~50 MB/sec write
```

These will repeatedly justify your design choices in later steps.

---

## Checklist

- [ ] Wrote down DAU, write RPS, read RPS, storage, bandwidth.
- [ ] Multiplied average by a peak factor and said it out loud.
- [ ] Identified which numbers force a non-trivial choice (sharding, cache, queue).
- [ ] Explicitly noted what scale does **not** matter (e.g., admin APIs).
