# 14 · Streak System — Interviewer Follow-ups (the grill)

The questions a senior interviewer asks once they're convinced you can draw boxes. **Lead with a position, then justify.** Hedging is what entry-level looks like.

---

## Q1. "Why isn't this just a counter?"

A counter doesn't survive **multi-device races** (lost updates), **timezone differences** (when does the day flip?), or **cheating with offline events** (replay-and-grow). The core counter is the easy 5 % of the system; the other 95 % is concurrency, anti-cheat, configurability, and observability.

If they push: walk them through the multi-device race in `11_concurrency_and_scaling.md`.

---

## Q2. "Walk me through what happens if Redis is down."

1. `tryDedup` falls back to "treat as first event of day."
2. `daily_activity` UPSERT is idempotent on PK — no harm if we replay.
3. `streak_state` CAS still protects `current_streak`. If two events for same user race to DB, both compute the same new state from the same prior state; first commits, second's CAS fails, retries, sees the new state, returns NoOp.
4. Reads slow down (cache miss → DB), but stay correct.
5. We log `cache.unavailable` and alert.

The critical insight: **the cache is for cost, not correctness.** Correctness lives in the DB.

---

## Q3. "User opens app at 11:59 PM Monday, and again at 12:01 AM Tuesday. Does their streak go from N to N+1?"

Yes. Two distinct calendar days under day-basis math. We **explicitly reject** rolling-window logic (24h since last event) — that's a different feature.

Probe: "What if the user is in IST and the server is UTC?" → We use the user's TZ to compute the calendar day. We capture TZ on each event.

---

## Q4. "User flies from IST to PST mid-streak. Anything break?"

No. We compute "today" in user's *current* TZ on every read. `lastActiveDay` is a `LocalDate` (no TZ); the comparison `today - lastActiveDay <= 1` is calendar arithmetic. If they were active 11:30 PM IST yesterday and 9 AM PST today, those are still consecutive calendar days from any honest TZ math.

Pathological case: user manipulates TZ to skip a day. Mitigation: server snapshots TZ from authenticated session metadata, not arbitrary client claims. (V2.)

---

## Q5. "How do you scale to 1 B users?"

Three levers.
1. **Shard `daily_activity` by user_id** (Postgres FDW or move to Cassandra/Scylla). Calendar reads are user-scoped → clean shard fit.
2. **Shard `streak_state` by user_id.** Same key.
3. **Multi-region with eventual cross-region consistency.** User pinned to home region; admin config is single-writer, replicated.

The bottleneck is *not* compute; it's storage. 1 B users × 1 KB streak state = 1 TB — fine — but `daily_activity` becomes 100+ TB. That's where partitioning + cold-tier (S3) earns its keep.

---

## Q6. "What if Kafka is down?"

The host app continues; events queue in the producer. Streak Service stops ingesting. Reads remain correct (using last-known state). When Kafka recovers, events drain — idempotency layers absorb any duplicates, and our streak math is robust against backfilled events (`Backfilled` variant doesn't grow current streak).

User-visible impact: their streak isn't *advanced* during the outage. We can run a **compensation script** that scans all events from the outage window and forces backfill — but anti-cheat math means current streak stays correct.

If a user genuinely engaged on Monday but the event arrived Wednesday, they get a calendar mark for Monday but not a streak credit. **That's a deliberate tradeoff** — anti-cheat trumps perfection.

---

## Q7. "The PM wants to add streak freezes. How much do we change?"

Three places:
1. New aggregate: `StreakFreezeWallet { userId, balance }`.
2. Modify `StreakState.recordActivity` to accept a `forgivenDays: Set<LocalDate>` parameter and treat them as "active" for the purpose of contiguity.
3. New domain event: `StreakFreezeApplied`. New API: `GET/POST /v1/me/freezes`.

The streak math stays pure; freezes layer on top. The repo is unchanged. We add a new schema for `streak_freeze_wallet`.

This question is testing **how well your domain model absorbs change**. Our answer: well, because the algorithm is one method on one aggregate.

---

## Q8. "How do you test this?"

Three layers.

1. **Domain unit tests** — `StreakState.recordActivity` is pure. We test:
   - same day → NoOp
   - next day → Advanced
   - 2-day gap → Restarted
   - past day → Backfilled
   - longest stays max
   - version increments
2. **Service tests with in-memory repos** — exercise the CAS retry path; the dedup path; the cache invalidation path.
3. **Integration tests with Testcontainers (Postgres + Redis + Kafka)** — happy path, multi-device race (concurrent calls to `recordActivity`), Kafka redelivery.

For TZ correctness: parametrize tests over `[UTC, IST, PST, NZDT]`.

For idempotency: replay every event 3× in tests; assert exactly the same final state.

---

## Q9. "How do you migrate this to production with the existing app?"

1. **Shadow mode**: Streak Service consumes events but doesn't expose APIs. Compare server-computed vs offline-batch-computed streaks for a week.
2. **Read-only canary**: 1 % of users see the streak; rest don't. Compare engagement metrics for 2 weeks.
3. **Full rollout** with feature flag, gradual ramp.
4. **Backfill** for existing users from historical event log (if available) — bulk-import to `daily_activity`, derive `streak_state` per user.

Backfill is the risky part: 100 M users × 5 yr of events is a 100 TB job. We chunk by `user_id % N`, run on a separate cluster, write to a sandbox DB, validate, then promote.

---

## Q10. "Walk me through the failure of CAS when retries are exhausted."

Three retries × cheap reads + cheap writes ≈ ~30 ms total. Exhaustion implies pathological contention (e.g., a power user with 100 events/sec — likely a malfunctioning client).

Action:
1. Throw `ConcurrencyException`.
2. Caller (Ingestor) doesn't ACK the Kafka offset.
3. Event is redelivered.
4. By the time it's reprocessed, the contender has finished — retry succeeds.

Worst case: a pathological user makes the consumer slow. We rate-limit per `user_id` at the consumer (token bucket per partition key) to prevent one user from starving others.

---

## Q11. "What's the cost of this system?"

Approximate run cost:
- 4 Postgres nodes (1 primary, 2 replicas, 1 audit) × $1.5/hr ≈ $4.4 K/mo.
- 3 Redis nodes × $0.4/hr ≈ $864/mo.
- 30 + 20 = 50 service pods × $0.05/hr ≈ $1.8 K/mo.
- 6 Kafka brokers (often shared infra) ≈ $2 K/mo allocated.
- ~$10 K/mo total at 100 M users.

Compare to expected revenue lift from engagement: industry data says +5–10 % retention from streak features. At a per-user-month value of $1–10, even a small lift dwarfs the bill.

---

## Q12. "What's the biggest risk?"

Anti-cheat. If we get it wrong, we award badges to users gaming the system → other users notice → community trust erodes.

Specifically: the moment we let "late events grow current streak" (the `Backfilled` slip), there's an unbounded exploitation. We have integration tests for this case explicitly; it's the most-asserted invariant in the suite.

---

## Q13. "If you had two more weeks, what would you build?"

1. **Streak freezes** (highest user-loved feature).
2. **Per-segment admin config** (unblocks experimentation).
3. **Push reminders** (cheapest engagement lift).
4. **Personal-best history graph** (storytelling, retention).

Anything beyond that is dessert.

---

## Q14. "What did you cut to ship V1?"

- Streak freezes (acknowledged, deferred).
- Social/leaderboards (separate service, separate quarter).
- Custom admin rules (V1 has hard-coded classifiers).
- Multi-region writes (V1 single-region).
- Aggregate analytics dashboard (we ship raw metrics, BI builds dashboards).

The cut list is honest. Don't pretend everything is in V1.

---

## Q15. "Why didn't you use event sourcing?"

We *do* effectively have an event log — `daily_activity` is "one row per qualifying event-day." But we don't replay it on each read; we maintain a derived `streak_state` and update it incrementally.

Pure event sourcing pays off when:
- You have many derived views.
- You frequently change derivation rules retroactively.
- Audit/compliance demands replayability.

For us:
- One main view (current streak number).
- Rules change rarely; even then, V2 won't retroactively reclassify (we've decided this in `04_domain_model.md`).
- Audit is satisfied by the daily-activity log + immutable streak events on Kafka.

So we get most of the wins of ES (observability, ability to recompute) without the recompute cost on every read.

---

## Q16. "What if the admin sets active_type = LISTENING but most users have only APP_VISIT history? They'll all see streak = 0."

Right. That's why we **track both types in parallel** (CompositeClassifier) from day 1. Switching is a metadata change, not a data migration.

If the listening classifier was added *later*, we'd have a backfill window where users see streak = 0 for the new type. We mitigate by:
1. Running the new classifier in shadow mode for 4+ weeks before switching.
2. Backfilling LISTENING data from historical play events.
3. Rolling out the switch behind a per-segment flag (extension #2).

---

## Q17. "Streak goes from 99 to 100. Two milestones (50, 100) configured. Which fires?"

Only 100. Our milestone listener uses the half-open interval `(previousCurrent, currentStreak]` = `(99, 100]` — only contains 100. Day-by-day advancement makes this trivial; if we ever had a single update that jumps multiple thresholds (Backfill compensation), we'd fire all of them in order.

---

## Q18. "What's an interview-grade red flag in this system you'd expect a candidate to miss?"

Three:
1. Forgetting timezones — proposing UTC as the streak day.
2. Storing `is_alive` and proposing a daily cron to flip it.
3. Letting Backfilled events advance the current streak (anti-cheat hole).

I'd push back on each immediately and watch how the candidate adapts.

---

## Output

The streak system is small, but the staff-level concerns are large: timezones, idempotency, CAS, anti-cheat, admin races, hot-path cost. Owning them with explicit tradeoffs is the bar.

```
Drill questions covered:
- Counter naïveté    → multi-device + TZ + cheat
- Failure modes      → Redis, Kafka, Postgres
- TZ semantics       → user TZ, calendar arithmetic
- Scaling            → user-id sharding, monthly partitions, eventual multi-region
- Anti-cheat         → Backfilled doesn't advance current
- Migration          → shadow → canary → rollout → backfill
- Future-proofing    → freezes, per-segment, custom rules
- Risk               → anti-cheat
- ES question        → derived view + event log = best of both
```
