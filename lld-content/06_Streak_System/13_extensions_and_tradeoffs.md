# 13 · Streak System — Extensions & Tradeoffs

A staff-level design must articulate **what we deliberately deferred and how we'd add it later**. Each extension below is a vector along which the system can grow without rewriting V1.

---

## 1. Streak freezes / saves (Duolingo-style)

**Idea.** User has limited tokens. Spending one "saves" a missed day so the streak doesn't break.

**Design.**
- New aggregate `StreakFreezeWallet { userId, balance, earnedAt }`.
- New domain operation: `applyFreeze(missedDay)`.
- `StreakState.recordActivity` honors a `forgivenDays` set: `if eventDay - lastActiveDay - forgiven == 1`, advance.
- Freezes auto-apply on first qualifying event after a missed day, *up to balance*.

**Tradeoffs.**
- Pro: massive engagement boost (proven feature).
- Con: complicates streak math — "current streak" diverges from "calendar contiguous days."
- Con: new abuse vector (people farm freeze tokens).

**Why not in V1.** Cleanly orthogonal — domain stays pure; freezes layer on top.

---

## 2. Per-segment / per-cohort active type

**Idea.** A/B test: 10 % of users get LISTENING streak, 90 % stay on APP_VISIT.

**Design.**
- `AdminConfig` becomes `List<Rule>` where each rule is `{ predicate, activeType }`.
- Predicates: `userId hash bucket`, `country`, `experiment_flag`.
- `getActiveTypeForUser(u)` evaluates rules in order.

**Tradeoffs.**
- Pro: zero-downtime rollout; PMs get real numbers.
- Con: cache key now depends on `(user, type, rule_version)`.
- Con: aggregate metrics get harder ("DAU on streak" must be per-cohort).

**Migration path.** Start with `AdminConfig.activeStreakType` (V1). When we add rules, the singleton becomes "default rule."

---

## 3. Multiple types simultaneously

**Idea.** Show both APP_VISIT and LISTENING streaks side by side. Some users prefer the visit metric (passive), others the listening metric (active).

**Design.**
- API: `GET /v1/me/streaks` returns array of all types.
- UI: tab-switcher between streaks.
- Backend already tracks both — we just expose both. **Already supported.**

**Tradeoffs.**
- Pro: rich product surface.
- Con: PM must define which streak's milestone is "the badge." V1 ties milestones to active type only.

---

## 4. Weekly / monthly milestones

**Idea.** "Active 5 of 7 days this week" or "Active every Sunday for 4 weeks."

**Design.**
- New aggregate `WeeklyEngagement { userId, type, isoWeek, daysActive }`.
- Computed nightly from `daily_activity` (or incrementally on each new day).
- New milestone definition table includes a `kind` column: `DAILY` | `WEEKLY_X_OF_Y` | `MONTHLY`.
- Milestone listener becomes a strategy.

**Tradeoffs.**
- Pro: more granular product loops.
- Con: window definition gets opinionated (ISO week vs calendar week vs user's week-start preference).

---

## 5. Custom streak rules

**Idea.** Admin creates: "completed an episode" or "listened ≥ 60 minutes" or "added a podcast to favorites."

**Design.**
- `ActivityClassifier` interface already supports this. Add a `RuleEngineClassifier` that loads JSON rules from `admin_config`.
- Rules: `{ kind: EPISODE_PLAYED, conditions: { duration_seconds: { gte: 60 }, completed: true } }`.

**Tradeoffs.**
- Pro: PM-driven without redeploys.
- Con: must protect against pathological rules (regexes, infinite loops).
- Con: testing surface explodes; need a rule simulator.

---

## 6. Social / leaderboards

**Idea.** Show friends' streaks. Weekly leaderboard.

**Design.**
- New service: `SocialService` consumes `streak.events`.
- Materialized view: `friend_streaks(user, friend, type, current)`.
- Leaderboard: Redis sorted set per cohort, scored by `current`.

**Tradeoffs.**
- Pro: viral growth driver.
- Con: privacy — opt-in required.
- Con: graph queries get expensive at 100 M users + sparse friend graph.

---

## 7. Push reminders

**Idea.** At 8 PM in user's TZ, if they haven't yet engaged today, send a push.

**Design.**
- Hourly scheduler (per timezone bucket): query `streak_state` where `last_active_day < today_in_tz` and `current > 0`.
- Send push via NotificationService with cooldown.

**Tradeoffs.**
- Pro: documented +20 % retention in similar features.
- Con: notification fatigue if not throttled.
- Con: query is `WHERE last_active_day < ? AND current > 0` — not blazing fast at scale; need a partial index `WHERE current > 0`.

---

## 8. Streak share cards (image generation)

**Idea.** "I'm on a 30-day streak!" shareable image.

**Design.** Out-of-band image renderer (SVG → PNG via Resvg). Pre-generate top milestone cards.

**Tradeoffs.** Trivial; isolated service.

---

## 9. Personal best history

**Idea.** Show user a graph of their streak length over time — the "all-time highs" chart.

**Design.**
- New table `streak_history(user_id, type, snapshot_date, current, longest)`.
- Cron once/day per user — append-only.
- Or: derived from `daily_activity` at read time (fully reconstructible).

**Tradeoffs.**
- Pre-aggregating beats real-time reconstruction at scale.

---

## 10. GDPR — export & delete

**Idea.** User exports all streak data; or requests deletion.

**Design.**
- Export: ZIP of `streak_state`, `daily_activity`, `milestone_award`.
- Delete: soft-delete user_id in all tables; hard-delete after 30-day grace.

**Tradeoffs.** Mandatory; design from day 1 (we did — `user_id` is the partition key everywhere).

---

## Tradeoff dossier — pre-decided answers

### Postgres vs Cassandra for `daily_activity`

| Criterion | Postgres | Cassandra |
| --- | --- | --- |
| Write throughput @ 10× | tight | comfortable |
| Calendar range scan | great (PK + partition) | great (clustering) |
| Operational maturity in our stack | high | low |
| Dev velocity | high | medium |
| Cost @ scale | medium | low |
| Decision (V1) | **Postgres** ✓ | (V2) |

### Optimistic vs pessimistic locking

| Criterion | Optimistic | Pessimistic |
| --- | --- | --- |
| Conflict probability | ~0.1 % | irrelevant |
| Per-call latency | low + retry tail | medium constant |
| Concurrency model | lock-free | lock-bound |
| Decision | **Optimistic** ✓ | only on admin config |

### Compute on read vs daily cron for `is_alive`

| Criterion | Compute on read | Daily cron |
| --- | --- | --- |
| Storage cost | 0 | adds `is_alive` col |
| Cron complexity | none | per-TZ scheduling |
| Race with writes | none | high |
| Decision | **Compute on read** ✓ | rejected |

### Track all types vs only active type

| Criterion | All | Only active |
| --- | --- | --- |
| Storage | ~2× | ~1× |
| Admin switch UX | seamless | requires backfill |
| Decision | **All** ✓ | rejected |

### Idempotency on event_id vs (user, type, day)

| Criterion | event_id | (user, type, day) |
| --- | --- | --- |
| Cardinality | 240 M / day | 30 M / day |
| Catches retries | yes | yes |
| Catches multi-device | no | yes |
| Decision | **(user, type, day)** ✓ for streak math; event_id for analytics dedup |

### Strict vs eventual cross-region

| Criterion | Strict | Eventual |
| --- | --- | --- |
| Engagement feature SLA | seconds-OK | yes |
| Cost (multi-region writes) | high | low |
| Decision | **Eventual cross-region**, strict per-region ✓ |

---

## What we still don't know (interview answer: "I'd ask")

- Exact ratio of DAU to MAU (drives capacity).
- Existing identity/auth contract.
- Whether the host app already has a global event bus (Kafka or proprietary).
- Marketing's milestone design (badges? coins? UI placement?).
- Legal retention requirements per region.

Surfacing unknowns is a stronger signal than guessing.

## Output

```
Extensions:    freezes, segments, multi-type, weekly/monthly,
               custom rules, social, reminders, share cards
Pre-decided:   Postgres+CAS, compute-on-read, track-both-types,
               (user,type,day) dedup, eventual cross-region
Open Qs:       DAU/MAU, host event bus, legal retention
```
