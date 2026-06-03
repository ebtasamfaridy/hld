# 08 · Streak System — Sequence Diagrams

The four flows you must be able to whiteboard:
1. Recording activity (cache-first, idempotent).
2. Reading the streak.
3. Reading the calendar.
4. Admin switching the active streak type.
5. Milestone fire (async).

## 1. Recording activity (the hot path)

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka(app.events)
    participant ING as Ingestor
    participant CL as Classifier
    participant SS as StreakService
    participant R as Redis
    participant PG as Postgres
    participant OUT as Kafka(streak.events)

    K->>ING: consume(rawEvent)
    ING->>CL: classify(rawEvent)
    CL-->>ING: Optional<ClassifiedEvent> (drop if empty)

    ING->>SS: recordActivity(classifiedEvent)
    SS->>SS: eventDay = floor(occurredAt, userTz)

    SS->>R: SETNX dedup:{u}:{t}:{day} 1, EX=26h
    alt already set (NOT first event of day)
        R-->>SS: 0
        SS->>PG: UPSERT daily_activity (count++)
        SS-->>ING: NoOp
        ING->>K: ack
    else first event of day
        R-->>SS: 1
        SS->>PG: INSERT daily_activity (...)
        SS->>R: GET streak:{u}:{t}
        alt cache hit
            R-->>SS: state
        else cache miss
            R-->>SS: nil
            SS->>PG: SELECT streak_state ...
            PG-->>SS: state
        end
        SS->>SS: update = state.recordActivity(eventDay)
        loop CAS retry (≤3)
            SS->>PG: UPDATE streak_state ... WHERE version=?
            alt success
                PG-->>SS: 1 row
            else CAS failed
                PG-->>SS: 0 rows
                SS->>PG: re-read state
                SS->>SS: re-apply
            end
        end
        SS->>R: SET streak:{u}:{t} new_state, TTL=user_midnight+1h
        SS->>OUT: publish StreakAdvanced / StreakBroken
        SS-->>ING: ack
        ING->>K: ack
    end
```

### Why the cache check first?

Most events are not the first of the day. SETNX is one Redis hop; failing fast avoids any DB work at all. Cost per non-first event ≈ 0.5 ms.

### Why CAS, not pessimistic lock?

Multi-device contention is rare (~1 in 1000 events) and short. Pessimistic locks add per-row latency on every event; CAS only pays the retry cost on actual conflict.

### What if the Kafka offset is acked before Postgres is committed?

It isn't. We commit the offset only **after** Postgres acks. If Ingestor crashes mid-flow, the same event is re-delivered; the dedup SETNX absorbs it (ttl > re-delivery window).

## 2. Reading the streak

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant GW as API Gateway
    participant SS as StreakService
    participant R as Redis
    participant PG as Postgres
    participant CFG as Redis(active_type)

    C->>GW: GET /v1/me/streak
    GW->>SS: getStreak(userId)
    SS->>CFG: GET admin:active_type
    CFG-->>SS: APP_VISIT
    SS->>R: GET streak:{u}:APP_VISIT
    alt cache hit
        R-->>SS: state
    else miss
        SS->>PG: SELECT * FROM streak_state WHERE user=? AND type=?
        PG-->>SS: state
        SS->>R: SET streak:{u}:APP_VISIT, TTL=user_midnight+1h
    end
    SS->>SS: today = LocalDate.now(state.userTimezone)
    SS->>SS: is_alive = today - last_active_day <= 1
    SS-->>GW: { current, longest, last_active_day, is_alive, today }
    GW-->>C: 200 OK
```

`is_alive` is **computed**, never stored. This is what lets us skip the daily-cron design.

## 3. Reading the calendar

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SS as StreakService
    participant R as Redis
    participant PG as Postgres

    C->>SS: GET /v1/me/streak/calendar?year=2025&month=6
    SS->>R: GET cal:{u}:2025-06:APP_VISIT (optional)
    alt cache hit
        R-->>SS: bitmap
        SS-->>C: { days: [...] }
    else miss
        SS->>PG: SELECT day, event_count FROM daily_activity\n  WHERE user=? AND type=? AND day BETWEEN '2025-06-01' AND '2025-06-30'
        PG-->>SS: rows (≤ 30)
        SS->>SS: fill missing days as inactive
        SS->>R: SET cal:{u}:2025-06:APP_VISIT, TTL=5m (current month)
        SS-->>C: { days: [...] }
    end
```

Past-month calendars are immutable → cache TTL = 7d.
Current month → TTL = 5m (so today's activity surfaces quickly).

## 4. Admin switching active streak type

```mermaid
sequenceDiagram
    autonumber
    participant ADM as Admin Console
    participant AS as AdminService
    participant PG as Postgres
    participant R as Redis
    participant K as Kafka(streak.events)

    ADM->>AS: PATCH /admin/streak-config { active: LISTENING }, If-Match: 12
    AS->>PG: UPDATE admin_config SET active='LISTENING', version=13\n  WHERE id=1 AND version=12
    alt 1 row updated
        PG-->>AS: ok
        AS->>R: SET admin:active_type LISTENING
        AS->>R: DEL streak:* (or bump prefix version)
        AS->>K: publish AdminConfigChanged
        AS-->>ADM: 200 OK
    else 0 rows (version mismatch)
        PG-->>AS: 0
        AS-->>ADM: 409 Conflict (current_version=13)
    end
```

### Cache invalidation on admin switch

We use a **prefix version** trick rather than `DEL streak:*`:

```
Redis key: streak:v{globalVersion}:{u}:{t}
admin:cache_version = 7   (incremented on every admin change)
```

Reads look up `admin:cache_version` (cached locally for 1 s), then key `streak:v7:{u}:{t}`. Switching version effectively invalidates the world without scanning. This pattern is captured in `00_End_To_End_LLD_Tutorial/08_database_design.md` as the "cache versioning" technique.

## 5. Milestone firing (async)

```mermaid
sequenceDiagram
    autonumber
    participant SS as StreakService
    participant K as Kafka(streak.events)
    participant MS as MilestoneService
    participant PG as Postgres
    participant N as NotificationService

    SS->>K: StreakAdvanced{ user, type, current=30 }
    K->>MS: deliver
    MS->>PG: SELECT milestones WHERE days <= 30 AND days >= prev_current
    PG-->>MS: [30]
    loop for each candidate
        MS->>PG: INSERT INTO milestone_award\n  ON CONFLICT (user,type,days) DO NOTHING
        alt inserted
            PG-->>MS: 1 row
            MS->>K: StreakMilestoneReached{ days=30 }
            K->>N: deliver
            N->>N: send push
        else already exists
            PG-->>MS: 0 rows
        end
    end
    MS-->>K: ack
```

Idempotency on `(user, type, milestone)` — replays don't double-award.

## 6. Edge case: late-arriving event (offline mode)

```mermaid
sequenceDiagram
    autonumber
    participant ING as Ingestor
    participant SS as StreakService
    participant R as Redis
    participant PG as Postgres

    Note over ING: occurredAt = 5 days ago (user was offline)
    ING->>SS: recordActivity(eventDay = today-5)
    SS->>R: SETNX dedup:{u}:{t}:today-5
    alt already set (already backfilled)
        R-->>SS: 0
        SS-->>ING: NoOp
    else first time
        R-->>SS: 1
        SS->>PG: UPSERT daily_activity (today-5)
        SS->>SS: state.recordActivity(today-5)
        Note over SS: returns Backfilled (eventDay < lastActiveDay)
        SS-->>ING: Backfilled (no streak_state change)
    end
```

`Backfilled` updates only the calendar — the **current** streak isn't grown by old events because that would let users game offline mode. Captured in `04_domain_model.md`.

## Output

```
recordActivity:  cache dedup → upsert daily_activity → CAS streak_state → publish
getStreak:       cache → fallback DB; is_alive computed
getCalendar:     range scan, fill gaps
admin switch:    DB CAS → cache version bump → publish
milestone:       async listener; idempotent on (user,type,days)
```

These five flows are everything the system does. Burn them in.
