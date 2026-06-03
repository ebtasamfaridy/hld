# 07 · Rate Limiter — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Domain (records & sealed types) =====
    class Request {
      <<record>>
      -String ip
      -String userId
      -String route
      -String apiKey
    }
    class RateKey {
      <<record>>
      -String family
      -String value
      +storeKey() String
    }
    class LimitConfig {
      <<record>>
      -long maxTokens
      -double refillPerSec
      -long windowSeconds
      +tokenBucket(burst, rate) LimitConfig$
      +fixedWindow(limit, windowSec) LimitConfig$
    }
    class Decision {
      <<sealed interface>>
    }
    class Allow {
      <<record>>
      +long remaining
      +long resetEpochMs
    }
    class Deny {
      <<record>>
      +Duration retryAfter
      +long limit
      +String violatedScope
    }
    Decision <|-- Allow
    Decision <|-- Deny

    %% ===== Strategy: KeyExtractor =====
    class KeyExtractor {
      <<interface>>
      +keysFor(request) List~RateKey~
    }
    class CompositeKeyExtractor {
      -List~KeyExtractor~ extractors
      +keysFor(request) List~RateKey~
    }
    KeyExtractor <|.. CompositeKeyExtractor
    CompositeKeyExtractor o-- "*" KeyExtractor

    %% ===== Atomic Store =====
    class Store~S~ {
      <<interface>>
      +compute(key, updater) S
      +getOrNull(key) S
    }
    class InMemoryStore~S~ {
      -ConcurrentMap~String,S~ map
      +compute(key, updater) S
      +getOrNull(key) S
    }
    Store <|.. InMemoryStore

    %% ===== Strategy: Algorithm =====
    class Algorithm {
      <<interface>>
      +check(key, config, cost, nowMs) Decision
    }
    class TokenBucketAlgorithm {
      -Store~State~ store
      +check(key, config, cost, nowMs) Decision
    }
    class TBState {
      <<record>>
      -double tokens
      -long lastRefillMs
    }
    class FixedWindowAlgorithm {
      -Store~Counter~ store
      +check(key, config, cost, nowMs) Decision
    }
    class FWCounter {
      <<record>>
      -long windowStartMs
      -long count
    }
    class SlidingCounterAlgorithm {
      -Store~Window~ store
      +check(key, config, cost, nowMs) Decision
    }
    class SlidingWindow {
      <<record>>
      -long curWindowStartMs
      -long curCount
      -long prevCount
    }
    Algorithm <|.. TokenBucketAlgorithm
    Algorithm <|.. FixedWindowAlgorithm
    Algorithm <|.. SlidingCounterAlgorithm
    TokenBucketAlgorithm o-- "1" Store
    TokenBucketAlgorithm *-- TBState
    FixedWindowAlgorithm o-- "1" Store
    FixedWindowAlgorithm *-- FWCounter
    SlidingCounterAlgorithm o-- "1" Store
    SlidingCounterAlgorithm *-- SlidingWindow

    %% ===== Top-level facade =====
    class RateLimiter {
      -KeyExtractor extractor
      -Map~String,LimitConfig~ configByFamily
      -Map~String,Algorithm~ algorithmByFamily
      -Clock clock
      +check(request) Decision
      +check(request, cost) Decision
    }
    RateLimiter o-- "1" KeyExtractor
    RateLimiter o-- "*" Algorithm : per family
    RateLimiter o-- "*" LimitConfig : per family
    RateLimiter ..> Decision
    RateLimiter ..> Request
```

---

## Class diagram

```mermaid
classDiagram
    class RateLimiter {
      -KeyExtractor extractor
      -LimitConfigProvider configs
      -AlgorithmRegistry algos
      -DenialAuditor auditor
      +check(request, cost) Decision
    }

    class KeyExtractor {
      <<interface>>
      +keysFor(req) List~RateKey~
    }
    class CompositeKeyExtractor

    class LimitConfigProvider {
      <<interface>>
      +configFor(key) LimitConfig
    }
    class StaticLimitConfigProvider

    class Algorithm {
      <<interface>>
      +check(key, config, cost, now) Decision
    }
    class TokenBucketAlgorithm
    class LeakyBucketAlgorithm
    class FixedWindowAlgorithm
    class SlidingLogAlgorithm
    class SlidingCounterAlgorithm
    Algorithm <|.. TokenBucketAlgorithm
    Algorithm <|.. LeakyBucketAlgorithm
    Algorithm <|.. FixedWindowAlgorithm
    Algorithm <|.. SlidingLogAlgorithm
    Algorithm <|.. SlidingCounterAlgorithm

    class Store {
      <<interface>>
      +get(key) State
      +update(key, state, ttl)
      +script(name, keys, args) Object
    }
    class InMemoryStore
    class RedisStore
    Store <|.. InMemoryStore
    Store <|.. RedisStore

    class Decision {
      <<sealed>>
    }
    class Allow
    class Deny
    Decision <|-- Allow
    Decision <|-- Deny

    KeyExtractor <|.. CompositeKeyExtractor
    LimitConfigProvider <|.. StaticLimitConfigProvider

    RateLimiter o-- KeyExtractor
    RateLimiter o-- LimitConfigProvider
    RateLimiter o-- Algorithm
    Algorithm   o-- Store
```

## Package layout

```
com.ratelimit
├── domain/         RateKey, LimitConfig, AlgorithmType, Decision (sealed)
├── algorithm/      Algorithm + impls (TokenBucket etc.)
├── store/          Store + InMemoryStore + RedisStore (interface only here; Lua lives as resource)
├── middleware/     KeyExtractor + CompositeKeyExtractor; HttpRateLimiterFilter (sketch)
├── RateLimiter.java
└── Main.java
```

## How algorithms talk to store

We could let each Algorithm own its own Store accesses (more flexible), or push state I/O into a generic `Store.compareAndUpdate` (simpler).

V1: each algorithm does its own store access (flexible — Token Bucket reads/writes a Hash; Sliding Log reads/writes a ZSET).

For Redis, the algorithm calls a **named Lua script** with KEYS+ARGS; the script encapsulates the whole algorithm. This is the **production pattern**.

## Output

A small graph: `RateLimiter` orchestrates Extractor + Config + Algorithm. Each Algorithm owns its store interactions. Decisions are sealed.
