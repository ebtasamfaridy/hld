# 01 · Streak System — Requirements

## Problem statement

We have an existing app (e.g., a podcast / music app). We want to **introduce a streak feature** that rewards users for engaging with the app every calendar day. The streak is per user, on a **day basis** (calendar-day, not rolling 24h window). It breaks if the user skips a single day.

We design the **backend module** that plugs into the existing app. The host app already emits events (login, screen view, episode play). Our system consumes those events and exposes streak APIs.

---

## Functional requirements

### Core (in scope)

**User-facing**
- View **current streak** (number of consecutive days).
- View **longest streak** (personal best).
- View **calendar** of activity for any month: which days were "active," which were missed, today's status (active / inactive / pending).
- See current streak type configured by admin (visit vs listening).
- Receive milestone events (7 / 30 / 100 / 365 days) — surfaced in app UI / push.

**Admin-facing**
- Choose which streak type is **active globally** (or per-segment, see extensions):
  - `APP_VISIT` — any session start qualifies.
  - `LISTENING` — listening to a playlist/episode for ≥ N seconds (e.g., 30s) qualifies.
- View aggregate streak metrics (DAU, % users on a streak, distribution by length).
- Manually adjust a user's streak (compensation for app outages).

**Platform-facing**
- Consume **activity events** from the existing app (`UserSessionStarted`, `EpisodePlayed`, etc.) idempotently.
- Compute streaks in the **user's local timezone** (otherwise a Tokyo user "loses" their streak unfairly when the server's UTC day rolls over).
- Fire **milestone events** on streak length thresholds.
- Provide a calendar API with O(days) cost.

### Extensions (acknowledged, not built today)

- **Streak freezes / saves** — let user spend a coin to skip a day (Duolingo-style).
- **Per-segment streak type** (some users in test cohorts have LISTENING, others APP_VISIT).
- **Multiple streak types simultaneously** — track both visit and listening side-by-side.
- **Weekly / monthly milestones** beyond the day-streak (e.g., "active 5 of 7 days this week").
- **Custom streak rules** (listened ≥ 30 minutes, completed an episode).
- **Social** — friends' streaks, leaderboards.
- **Push reminders** at evening if user hasn't engaged today.
- **Streak share cards**.

### Out of scope

- The app itself.
- The auth / identity service (we receive `user_id` from upstream).
- Push provider internals.
- Analytics warehouse internals.

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Activity ingest p99 | < 50 ms | Hot path; must not slow user actions |
| Streak read p99 | < 100 ms | Shown on home screen |
| Calendar read p99 | < 200 ms | Less frequent |
| Throughput | 50 K events/sec peak (product-wide) | Big-app scale |
| Strong consistency | per-(user, day) idempotency, current-streak counter | Avoid drift |
| Eventual consistency | aggregate metrics, calendar | Acceptable lag (seconds) |
| Availability | 99.95 % | Engagement feature, not money |
| Storage | per-user activity for 5 yr | Calendar + history |
| Timezones | per-user TZ honored | Critical for fairness |

---

## Actors

```
User                   - subject of the streak; views own data
Existing App           - emits activity events; calls streak read APIs
Streak Service         - this system
Admin / PM             - configures active streak type
NotificationService    - external; sends milestone pushes / reminders
EventBus (Kafka)       - upstream activity events
TimeService            - injectable clock for testability
```

---

## Edge cases (and decisions)

| Case | Handling |
| --- | --- |
| User opens app at 11:58 PM and 12:01 AM (TZ midnight) | Both days count; streak +1 (assuming yesterday was also active) |
| User in different TZ traveling | Snapshot user's TZ on event; recompute "day" using event's TZ context |
| Multiple devices ping concurrently same day | Idempotent: (user, day, type) primary key — duplicates absorbed |
| Late-arriving event for yesterday | Apply to yesterday's day-bucket; streak math is from latest day forward, so retroactive doesn't *retroactively grow* current streak; exception path documented |
| Admin switches streak type from VISIT → LISTENING | New events count under LISTENING; **historical days** under VISIT are not retroactively reclassified — they're stored separately |
| User has been inactive 5 days, then visits | Current streak = 1 (broken), longest_streak unchanged, calendar shows the gap |
| Server clock skew | Trust user's device timestamp where possible; clamp to [now-5min, now+5min] window to prevent abuse |
| User on a streak, app has outage all day | Admin can grant "free pass" via streak adjustment API |
| User deletes account | Streak data soft-deleted, retained for legal period |
| Activity event arrives 30 days late (offline mode) | Apply to the historical day; current-streak unaffected; calendar updated |
| User changes their TZ | Stored TZ updates from next event; previously stored day buckets stay |
| Streak hits 365 days, then 366 | Both milestones fire if both configured; idempotent |

---

## Two key clarifications worth nailing in the interview

1. **Day basis vs window.**
   "Day basis" means: a streak counter goes up if user has any qualifying activity in a calendar day. It does **not** mean "every 24h since last activity" — that would be a rolling window.

   Example: visit Monday 11:58 PM, visit Tuesday 12:01 AM → streak goes from 1 to 2. (Window-based would count this as one event.)

2. **Whose calendar day?**
   The user's. Not server time. We store the user's timezone on each event (or on the user profile). A user in IST traveling to PST should not lose a streak because of TZ.

---

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| App-visit streak | ✓ | |
| Listening streak | ✓ | |
| Admin global toggle | ✓ | |
| Calendar view (month) | ✓ | |
| Milestones (7/30/100/365) | ✓ | |
| Per-segment toggle | | ✓ |
| Streak freezes / saves | | ✓ |
| Multiple types in parallel | | ✓ |
| Weekly / monthly milestones | | ✓ |
| Social / leaderboards | | ✓ |
| Reminders | | ✓ |

---

## Output

```
Actors:        User, App (event producer), StreakService, Admin, Notification, Kafka
Core FR:       ingest events, compute daily streak (day-basis, user TZ),
               calendar view, milestones, admin-configurable type
NFR:           p99 50 ms ingest, 50 K events/sec peak, strong on (user,day) idempotency
Out of Scope:  app itself, auth, push internals, analytics warehouse
Extensions:    freezes, per-segment, multi-type, social, reminders
```

This contract is what we now design against. Every subsequent file refers back to it.
