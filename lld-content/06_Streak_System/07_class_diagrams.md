# 07 · Streak System — Class Diagrams

Class-level structure of the Streak Service, drawn at the granularity an interviewer expects you to whiteboard.

## Layered package map

```mermaid
flowchart TB
  subgraph api[API Layer]
    StreakController
    AdminController
  end

  subgraph app[Application Layer]
    StreakService
    AdminService
    MilestoneService
    ActivityIngestor
  end

  subgraph domain[Domain Layer]
    StreakState
    DailyActivity
    AdminConfig
    MilestoneAward
    ActivityClassifier
  end

  subgraph infra[Infrastructure Layer]
    StreakStateRepository
    DailyActivityRepository
    AdminConfigRepository
    MilestoneAwardRepository
    EventBus
    Clock
    RedisCache
  end

  api --> app
  app --> domain
  app --> infra
```

## Core domain classes

```mermaid
classDiagram
  class StreakState {
    +UserId userId
    +StreakType streakType
    +int current
    +int longest
    +LocalDate lastActiveDay
    +ZoneId userTimezone
    +long version
    +Instant updatedAt
    +recordActivity(eventDay, tz) StreakUpdate
    +isAlive(today) boolean
  }

  class StreakUpdate {
    <<sealed>>
    +Kind kind
    +StreakState newState
    +int previousCurrent
    +int newCurrent
  }

  class DailyActivity {
    +UserId userId
    +StreakType streakType
    +LocalDate day
    +int eventCount
    +Instant firstEventAt
    +Instant lastEventAt
    +ZoneId userTimezone
    +incrementWith(eventAt) DailyActivity
  }

  class AdminConfig {
    +StreakType activeStreakType
    +List~int~ milestones
    +long version
    +setActiveType(t) AdminConfig
    +addMilestone(d) AdminConfig
  }

  class MilestoneAward {
    +UserId userId
    +StreakType streakType
    +int milestoneDays
    +Instant achievedAt
  }

  StreakState --> StreakUpdate
  StreakUpdate --> StreakState
```

`StreakState.recordActivity(...)` is **pure** — it returns a new `StreakState` and a `StreakUpdate` discriminated union. The application layer interprets the update.

## Strategy: classification

```mermaid
classDiagram
  class ActivityClassifier {
    <<interface>>
    +classify(RawAppEvent) Optional~ClassifiedEvent~
  }

  class AppVisitClassifier {
    +classify(e) Optional~ClassifiedEvent~
  }

  class ListeningClassifier {
    +int minSeconds
    +classify(e) Optional~ClassifiedEvent~
  }

  class CompositeClassifier {
    +List~ActivityClassifier~ classifiers
    +classify(e) Optional~ClassifiedEvent~
  }

  ActivityClassifier <|.. AppVisitClassifier
  ActivityClassifier <|.. ListeningClassifier
  ActivityClassifier <|.. CompositeClassifier
```

`CompositeClassifier` runs multiple classifiers in order; we use it because we *track both types in parallel* even though only one is "active." (Why: switching active type shouldn't lose history — see `04_domain_model.md`.)

## Application layer

```mermaid
classDiagram
  class StreakService {
    -StreakStateRepository stateRepo
    -DailyActivityRepository activityRepo
    -ActivityClassifier classifier
    -EventBus bus
    -Clock clock
    -RedisCache cache
    +recordActivity(RawAppEvent)
    +getStreak(userId, type) StreakSnapshot
    +getCalendar(userId, type, year, month) Calendar
  }

  class AdminService {
    -AdminConfigRepository repo
    -EventBus bus
    -RedisCache cache
    +setActiveType(t, expectedVersion) AdminConfig
    +getConfig() AdminConfig
    +adjustUserStreak(userId, op) StreakState
  }

  class MilestoneService {
    -MilestoneAwardRepository repo
    -AdminConfigRepository configRepo
    -EventBus bus
    +onStreakAdvanced(StreakAdvanced)
  }

  class ActivityIngestor {
    -StreakService service
    +consume(RawAppEvent)
  }

  StreakService --> ActivityClassifier
  StreakService --> EventBus
  ActivityIngestor --> StreakService
  MilestoneService ..> EventBus : subscribes
```

### Why `StreakService` is one class with three methods

`recordActivity`, `getStreak`, and `getCalendar` share the same dependencies (cache, repos, clock). Splitting them across services would create cycles or duplicate wiring. Internally each method is small (≤ 30 lines) and delegates to repos; SRP is preserved by domain methods, not by service splitting.

## Repository interfaces (ports)

```mermaid
classDiagram
  class StreakStateRepository {
    <<interface>>
    +findByUserAndType(uid, t) Optional~StreakState~
    +saveWithCas(state) boolean
  }

  class DailyActivityRepository {
    <<interface>>
    +incrementOrCreate(uid, t, day, eventAt, tz)
    +findByMonth(uid, t, year, month) List~DailyActivity~
  }

  class AdminConfigRepository {
    <<interface>>
    +get() AdminConfig
    +saveWithCas(cfg, expectedVersion) boolean
  }

  class MilestoneAwardRepository {
    <<interface>>
    +existsFor(uid, t, days) boolean
    +saveIfAbsent(award) boolean
  }
```

Repositories return domain objects, not rows. SQL/Redis details live in adapters.

## Event bus and events

```mermaid
classDiagram
  class EventBus {
    <<interface>>
    +publish(DomainEvent)
    +subscribe(Class~T~, Listener~T~)
  }

  class DomainEvent {
    <<sealed>>
  }

  class ActivityRecorded { +UserId userId; +StreakType type; +LocalDate day }
  class StreakAdvanced { +int prev; +int current; +int longest }
  class StreakBroken { +int previousLongest }
  class StreakMilestoneReached { +int milestoneDays }
  class AdminConfigChanged { +StreakType oldType; +StreakType newType }

  DomainEvent <|-- ActivityRecorded
  DomainEvent <|-- StreakAdvanced
  DomainEvent <|-- StreakBroken
  DomainEvent <|-- StreakMilestoneReached
  DomainEvent <|-- AdminConfigChanged
```

In V1 the bus is in-process (delegates to a Kafka producer at the edge). In V2 we can replace with a Kafka-direct adapter without touching listeners.

## Cache abstraction

```mermaid
classDiagram
  class RedisCache {
    <<interface>>
    +getStreak(uid, t) Optional~StreakSnapshot~
    +putStreak(snapshot, ttl)
    +tryDedup(uid, t, day) boolean
    +getActiveType() StreakType
    +setActiveType(t)
    +invalidateStreaks()
  }
```

`tryDedup` returns `true` when this is the first event for `(user, type, day)` — driven by a Redis `SETNX`.

## Why `StreakState.recordActivity` returns a `StreakUpdate` (not void)

Returning a discriminated union forces the caller to handle each case explicitly:

```java
public sealed interface StreakUpdate permits NoOp, Advanced, Backfilled, Restarted {
    record NoOp(StreakState state) implements StreakUpdate {}
    record Advanced(StreakState newState, int previousCurrent, int newCurrent)
            implements StreakUpdate {}
    record Backfilled(StreakState state) implements StreakUpdate {}
    record Restarted(StreakState newState, int previousLongest)
            implements StreakUpdate {}
}
```

The application layer maps:
- `Advanced` → publish `StreakAdvanced`, persist new state.
- `Restarted` → publish `StreakBroken` first, then `StreakAdvanced(1)`, persist.
- `Backfilled` → upsert `daily_activity` only, leave `streak_state` alone.
- `NoOp` → ignore.

This is **encoding the state machine in types**. No `boolean` flags, no nulls, no implicit branches.

## Output

A clean separation: **domain** is small (algorithms, invariants), **application** is glue, **infrastructure** hides the DB/cache, **events** decouple downstream concerns. The whole core fits in ~300 lines of Java.
