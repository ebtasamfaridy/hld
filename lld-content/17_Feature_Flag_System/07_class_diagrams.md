# 07 · Feature Flag System — Class Diagrams

## SDK / evaluator side (the "hot" classes)

```mermaid
classDiagram
    class FeatureFlags {
      <<interface>>
      +isOn(key, ctx) boolean
      +variation(key, ctx, default) string
      +evaluate(key, ctx) EvaluationResult
    }

    class FeatureFlagClient {
      -store: FlagStore
      -evaluator: Evaluator
      -bucketing: Bucketing
      +isOn / variation / evaluate
    }

    class FlagStore {
      <<interface>>
      +get(key) Flag
      +put(flag)
      +remove(key)
      +version() long
      +addListener(listener)
    }
    class InMemoryFlagStore
    FlagStore <|.. InMemoryFlagStore

    class Evaluator {
      -bucketing: Bucketing
      +evaluate(flag, ctx, store) EvaluationResult
    }

    class Bucketing {
      <<interface>>
      +bucket(salt, userId) int   "0..9999"
    }
    class HashBucketing
    Bucketing <|.. HashBucketing

    class FlagSubscriber {
      <<interface>>
      +start()
      +stop()
    }
    class HttpPollingSubscriber
    class SseSubscriber
    FlagSubscriber <|.. HttpPollingSubscriber
    FlagSubscriber <|.. SseSubscriber

    FeatureFlags <|.. FeatureFlagClient
    FeatureFlagClient o-- FlagStore
    FeatureFlagClient o-- Evaluator
    Evaluator o-- Bucketing
    FeatureFlagClient ..> FlagSubscriber
```

## Domain side

```mermaid
classDiagram
    class Flag {
      -key, environment, enabled
      -variations: List~Variation~
      -targetingRules: List~Rule~
      -prerequisites: List~Prereq~
      -fallthroughVariationId
      -offVariationId
      -version: long
    }

    class Rule {
      -conditions: List~Condition~
      -kind: FIXED | PERCENTAGE
      -variationId?
      -rolloutPercentage?
      +matches(ctx) boolean
    }

    class Condition {
      -attribute, operator, values
      +matches(ctx) boolean
    }

    class Operator {
      <<interface>>
      +matches(actual, expected) boolean
    }
    class EqOperator
    class InOperator
    class PrefixOperator
    class RegexOperator
    Operator <|.. EqOperator
    Operator <|.. InOperator
    Operator <|.. PrefixOperator
    Operator <|.. RegexOperator

    Flag o-- Rule
    Rule o-- Condition
    Condition ..> Operator
```

## Server side

```mermaid
classDiagram
    class FlagAdminService {
      -repo: FlagRepository
      -bus: UpdateBus
      -audit: AuditRepository
      +createFlag(env, payload, actor)
      +updateFlag(env, key, payload, expectedVersion, actor)
      +archiveFlag(env, key, actor)
    }
    class FlagRepository
    class AuditRepository
    class UpdateBus

    FlagAdminService o-- FlagRepository
    FlagAdminService o-- AuditRepository
    FlagAdminService o-- UpdateBus
```

## Package layout (`com.featureflags`)

```
api/        FeatureFlags, FeatureFlagClient, EvaluationContext, EvaluationResult
core/       Flag, Variation, Rule, Condition, Prereq
rule/       Operator + EqOperator/InOperator/PrefixOperator/RegexOperator
context/    EvaluationContextBuilder
store/      FlagStore (interface) + InMemoryFlagStore
client/     FlagSubscriber + HttpPollingSubscriber/SseSubscriber (stub)
            Evaluator + Bucketing/HashBucketing
```

## Why these abstractions

### `Operator` as Strategy
New operators get added all the time (`SEMVER_GT`, `WITHIN_DISTANCE`). Plug-in interface; the framework iterates known operators by name.

### `FlagStore` as an interface
In-memory for the SDK; Postgres-backed for the server. Same evaluator works against either.

### `Bucketing` as an interface
Hash function is configurable: SHA-1 (LaunchDarkly), Murmur3, FNV. Same interface; the framework benchmarks and picks based on hot-path requirements.

### `FlagSubscriber` as a strategy
Polling for low-infra, SSE for low-latency, GRPC for advanced. Same interface; pluggable.

### `Evaluator` separated from `FeatureFlagClient`
The client manages the store + subscriber + lifecycle. The evaluator is a pure function of `(Flag, EvaluationContext)`. Easy to test in isolation.

## Output

```
SDK side:    FeatureFlagClient → FlagStore + Evaluator + Subscriber
Server side: FlagAdminService → FlagRepository + AuditRepository + UpdateBus
Strategies:  Operator (per condition type), Bucketing (hash algo), Subscriber (transport)
Pure:        Evaluator is a deterministic function of flag + context
```
