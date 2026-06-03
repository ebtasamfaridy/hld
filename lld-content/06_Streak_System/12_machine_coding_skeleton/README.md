# 12 · Streak System — Machine Coding Skeleton

In-memory Java skeleton that demonstrates the **hot path** end-to-end: classify event → dedup → upsert daily activity → CAS streak state → publish events → listener fires milestones.

> The brief said theoretical only — this code is here as a reference shape, not as a runnable artifact. It compiles in principle (Java 17) but is not built/tested in CI.

## Layout

```
src/main/java/com/streak/
├── domain/                 # value objects, aggregates, sealed updates
│   ├── UserId.java
│   ├── StreakType.java
│   ├── RawAppEvent.java
│   ├── ClassifiedEvent.java
│   ├── StreakState.java
│   ├── StreakUpdate.java
│   ├── DailyActivity.java
│   ├── AdminConfig.java
│   ├── MilestoneAward.java
│   ├── StreakSnapshot.java
│   └── CalendarDay.java
├── classifier/             # Strategy pattern
│   ├── ActivityClassifier.java
│   ├── AppVisitClassifier.java
│   ├── ListeningClassifier.java
│   └── CompositeClassifier.java
├── repository/             # ports + in-memory adapters
│   ├── StreakStateRepository.java
│   ├── DailyActivityRepository.java
│   ├── AdminConfigRepository.java
│   ├── MilestoneAwardRepository.java
│   └── InMemory*.java
├── cache/                  # Redis abstraction
│   ├── StreakCache.java
│   └── InMemoryStreakCache.java
├── service/                # application + events
│   ├── EventBus.java
│   ├── InMemoryEventBus.java
│   ├── DomainEvent.java
│   ├── StreakService.java
│   ├── AdminService.java
│   └── MilestoneListener.java
└── Main.java               # demo runner
```

## Demo flow (`Main.java`)

1. Bootstrap: repos, cache, event bus, classifiers, services.
2. Seed admin config = `APP_VISIT`.
3. Day 1 — Alice opens the app twice → first event advances streak to 1, second is NoOp via dedup.
4. Day 2 — Alice opens the app, Bob plays an episode for 60s → both progress respective streaks (parallel tracking).
5. Day 3 — Alice skips. Bob plays again.
6. Day 4 — Alice comes back → her streak restarts at 1 (StreakBroken + StreakAdvanced fired).
7. Bob hits a 7-day streak (simulated by fast-forwarding clock) → milestone fires once.
8. Admin switches active type to LISTENING → streak reads now report Bob's listening streak.
9. Calendar view for Alice for the month.

## Run (conceptual)

```bash
javac --release 17 -d out $(find src -name '*.java')
java -cp out com.streak.Main
```

Expected output is a sequence of state transitions, demonstrating dedup, CAS retries, milestone firing, and admin switching.

## Why this is enough for an interview

In a 60–90 minute live machine-coding round, you'd ship the **domain layer + StreakService + dedup + CAS + a single classifier** and stub the rest. This skeleton shows what the "polished" version looks like; trim it to the minimum required by the prompt.
