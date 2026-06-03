# 10 · Streak System — Design Patterns

Each pattern below is justified against an explicit problem in this system. The "why this pattern" is what an interviewer probes — pattern *names* are the cheap part.

## 1. Strategy — `ActivityClassifier`

**Problem.** The rule for "what counts as activity" must be swappable: app-visit today, listening-with-min-seconds tomorrow, "completed an episode" later.

**Pattern.**
```java
public interface ActivityClassifier {
    Optional<ClassifiedEvent> classify(RawAppEvent event);
}

public final class AppVisitClassifier implements ActivityClassifier {
    public Optional<ClassifiedEvent> classify(RawAppEvent e) {
        if (e.kind() != Kind.SESSION_STARTED) return Optional.empty();
        if (e.metadata().getOrDefault("source","").equals("background_refresh")) return Optional.empty();
        return Optional.of(new ClassifiedEvent(e, StreakType.APP_VISIT));
    }
}

public final class ListeningClassifier implements ActivityClassifier {
    private final int minSeconds;
    public Optional<ClassifiedEvent> classify(RawAppEvent e) {
        if (e.kind() != Kind.EPISODE_PLAYED) return Optional.empty();
        int sec = (int) e.metadata().getOrDefault("duration_seconds", 0);
        if (sec < minSeconds) return Optional.empty();
        return Optional.of(new ClassifiedEvent(e, StreakType.LISTENING));
    }
}
```

**Where it lands.** Composed via `CompositeClassifier` — we run **both** classifiers on every event so we always have data for both streak types regardless of the active admin choice. Switching active type via admin is then just a read-side filter.

**Tradeoff.** We classify twice per event. Each classifier is O(1); the cost is negligible vs. the alternative (re-running history when admin switches).

## 2. Repository — separating domain from persistence

**Problem.** Domain logic (`StreakState.recordActivity`) must be pure and testable. SQL must not leak into the domain layer.

**Pattern.** Each aggregate has a repository interface in the domain package; adapters live in `infrastructure`.

```java
public interface StreakStateRepository {
    Optional<StreakState> findByUserAndType(UserId u, StreakType t);
    boolean saveWithCas(StreakState updated);   // CAS on version
}
```

Why **`saveWithCas` returns boolean** instead of throwing: the application layer wants to *retry* on CAS failure, not catch exceptions. Booleans express "expected failure"; exceptions express "unexpected."

## 3. Optimistic Concurrency Control (CAS)

**Problem.** Two devices send activity events simultaneously for the same user. Both read `streak_state` v=10, both compute `current=12`, both write — last-write-wins would silently double-count.

**Pattern.** `version` column, `UPDATE ... WHERE version=?`. Loser sees `0 rows updated`, re-reads, retries.

```java
boolean saveWithCas(StreakState s) {
    int rows = jdbc.update("""
        UPDATE streak_state
           SET current_streak=?, longest_streak=?, last_active_day=?, version=?, updated_at=now()
         WHERE user_id=? AND streak_type=? AND version=?
        """,
        s.current(), s.longest(), s.lastActiveDay(), s.version(),
        s.userId(), s.streakType(), s.version() - 1);
    return rows == 1;
}
```

In the application layer:
```java
for (int attempt = 0; attempt < 3; attempt++) {
    StreakState state = stateRepo.findByUserAndType(u, t).orElseGet(StreakState::empty);
    StreakUpdate upd = state.recordActivity(eventDay, tz);
    if (upd instanceof NoOp || upd instanceof Backfilled) return upd;
    if (stateRepo.saveWithCas(upd.newState())) return upd;
}
throw new ConcurrencyException("max retries");
```

**Why optimistic, not pessimistic.** Conflict probability is ~0.1 % at multi-device usage; pessimistic locking would serialize per-user updates, increasing p99 latency under load. Optimistic + ≤3 retries beats it on every metric except worst-case retry storms (which we cap).

## 4. Idempotency — multiple layers

**Problem.** A single user-action can produce multiple events (Kafka redelivery, client retry, multi-device).

**Layered defense:**

| Layer | Mechanism | Window |
| --- | --- | --- |
| HTTP | `Idempotency-Key` header | 24 h |
| Cache | Redis `SETNX dedup:{u}:{t}:{day}` | 26 h |
| DB (daily_activity) | PK on `(user, type, day)` + UPSERT | forever |
| DB (milestone_award) | UNIQUE on `(user, type, days)` + ON CONFLICT NO-OP | forever |

Each layer is *independently sufficient* for correctness; together they minimize cost (Redis absorbs 99 % before reaching Postgres).

## 5. Observer / Pub-Sub — domain events

**Problem.** Many things happen on a streak update: persist, notify, update analytics, trigger UI badge. Coupling all into `StreakService` is a god-class trap.

**Pattern.** `EventBus` in-process; downstream listeners.
```java
bus.publish(new StreakAdvanced(u, type, prev, current, longest));
bus.publish(new StreakBroken(u, type, previousLongest));
```

Listeners:
- `MilestoneListener` — checks thresholds, writes `milestone_award`.
- `MetricsListener` — increments counters.
- `KafkaPublisherListener` — forwards to `streak.events` topic.

`StreakService` is unaware of any of them.

## 6. Observer for milestones (idempotent)

**Problem.** Milestones are *derived facts*; computing them in the streak update loop bloats the hot path and couples notification to streak math.

**Pattern.** `MilestoneListener` consumes `StreakAdvanced` and uses `INSERT ... ON CONFLICT DO NOTHING` to avoid double-firing.

```java
@Listener
public void onAdvanced(StreakAdvanced e) {
    for (int days : config.milestones()) {
        if (e.previousCurrent() < days && days <= e.newCurrent()) {
            boolean inserted = milestoneRepo.saveIfAbsent(
                new MilestoneAward(e.userId(), e.type(), days, clock.now()));
            if (inserted) {
                kafka.publish("streak.events", new StreakMilestoneReached(...));
            }
        }
    }
}
```

The `(prev, current]` interval handling is what gives us idempotency *and* correctness when streaks jump by more than 1 (it doesn't here, but the code is robust).

## 7. Factory — `ClassifierFactory` for dynamic config

**Problem.** Admin can change config (e.g., `LISTENING.minSeconds = 60`). We don't want to reboot the service.

**Pattern.**
```java
public final class ClassifierFactory {
    private final AdminConfigRepository configRepo;
    private final AtomicReference<List<ActivityClassifier>> ref =
        new AtomicReference<>(List.of());

    public List<ActivityClassifier> current() { return ref.get(); }

    @Scheduled(every = "30s")
    void refresh() {
        AdminConfig c = configRepo.get();
        ref.set(List.of(
            new AppVisitClassifier(),
            new ListeningClassifier(c.listeningMinSeconds())
        ));
    }
}
```

The active **list** is published atomically. Hot path reads `factory.current()` once per event.

## 8. Cache versioning — invalidation without scans

**Problem.** Admin switches active type → all `streak:{u}:{type}` cache values may now show the wrong "current" type to clients. We can't `KEYS streak:*` (slow) and we can't `DEL` 30 M keys.

**Pattern.** A monotonically-increasing **prefix version**.
```
admin:cache_version = 7
client reads:    v = redis.get("admin:cache_version") (cached locally 1s)
                 redis.get("streak:v" + v + ":{u}:{t}")
admin switch:    redis.incr("admin:cache_version")  → 8
                 (all streak:v7:* keys are now unreachable; expire naturally)
```

We trade ~1 KB key-prefix duplication for O(1) global invalidation.

## 9. Specification — calendar query parameters

**Problem.** Admin metric pages need flexible queries: "users with current streak ≥ 7", "users who broke this week", etc. Hand-coded methods on the repository explode.

**Pattern.** Specification objects compile to SQL.
```java
public interface StreakSpec {
    String toSql();
    List<Object> params();
}

public record CurrentStreakAtLeast(int n) implements StreakSpec {
    public String toSql() { return "current_streak >= ?"; }
    public List<Object> params() { return List.of(n); }
}

public record AndSpec(StreakSpec a, StreakSpec b) implements StreakSpec { ... }
```

Query: `repo.findMatching(new AndSpec(new CurrentStreakAtLeast(7), new TypeIs(APP_VISIT)))`.

V1 ships hard-coded admin metrics; specification is documented as the path when admin needs grow.

## 10. Builder — `StreakStateSnapshot` for API responses

A small detail, but: API responses combine `StreakState` data + computed fields (`is_alive`, `today`). A builder keeps construction declarative.

```java
StreakSnapshot snap = StreakSnapshot.builder()
    .from(state)
    .today(LocalDate.now(state.userTimezone()))
    .computeIsAlive()
    .build();
```

## 11. Adapter — Kafka publisher / consumer

**Problem.** Domain knows nothing about Kafka. We may swap to NATS or in-process bus tomorrow.

**Pattern.**
- `EventBus` interface in domain.
- `InMemoryEventBus` for tests.
- `KafkaEventBusAdapter` in production (subscribes to in-memory bus, forwards to Kafka).

Same on consume side: `KafkaIngestor` adapts Kafka messages into `RawAppEvent`s, then calls the domain service.

## Pattern map (table)

| Pattern | Lives in | What it solves |
| --- | --- | --- |
| Strategy | `classifier/*` | Pluggable activity rules |
| Composite | `classifier/CompositeClassifier` | Run all classifiers per event |
| Repository | `repository/*` | Decouple domain from SQL |
| Optimistic CC | `StreakStateRepository.saveWithCas` | Multi-device race |
| Idempotency (layered) | HTTP + Redis + PG | Replays / retries |
| Observer (Domain Events) | `service/EventBus` | Decouple downstream concerns |
| Factory | `ClassifierFactory` | Hot reload of config |
| Cache versioning | Redis prefix `v{n}` | O(1) admin-driven invalidation |
| Specification | `admin/*Spec` | Flexible admin queries |
| Builder | `StreakSnapshot` | Construct response DTOs |
| Adapter | `KafkaEventBusAdapter` | Replaceable transport |

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| **State pattern (subclass per state)** | Only two real states (`Empty`, `Active`); enum + method suffices |
| **Saga / orchestrator** | No cross-service transaction; events are independent |
| **CQRS read store** | Postgres reads are already fast for our scale; YAGNI |
| **Event sourcing** | We have a daily-event log already (`daily_activity`); not strictly events, but enough — full ES adds rebuild complexity for no gain |

## Output

A small set of well-targeted patterns, each justified by a specific problem. The hot path (record activity) touches Strategy, CAS, Idempotency. The slow path (admin) touches Cache versioning, Specification. The integration boundary uses Adapter and Observer.
