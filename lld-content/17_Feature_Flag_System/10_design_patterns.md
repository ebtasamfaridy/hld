# 10 · Feature Flag System — Design Patterns

## 1. Strategy — `Operator`
Each operator (`EQ`, `IN`, `PREFIX`, `REGEX`, `SEMVER_GT`, …) is a small Strategy. The `Condition` looks up its operator at evaluation time.

## 2. Strategy — `Bucketing`
Hash function pluggable: SHA-1, Murmur3, FNV. Same interface; the framework chooses based on perf.

## 3. Strategy — `FlagSubscriber`
Polling, SSE, websocket, gRPC streaming. SDK chooses one at startup; same interface.

## 4. Repository — `FlagRepository`, `AuditRepository`
DB-agnostic abstraction; in-memory for tests, Postgres for prod.

## 5. Builder — `EvaluationContext.builder(userId)`
Rich context with optional attributes; builder sidesteps constructor explosion.

## 6. Event sourcing — audit log
Every change is a row. `before/after` states persisted. Replay reconstructs history. Compliance ready.

## 7. Pub/Sub — Kafka topic for updates
Server publishes; many SDKs subscribe via Edge SSE servers.

## 8. Observer — `FlagStore.addListener`
SDK code can react to flag updates: emit metrics, log, or trigger downstream changes.

## 9. Optimistic concurrency
Version field + `If-Match` on PUT. Two simultaneous admin edits cleanly resolve.

## 10. Discriminated union — `EvaluationResult.reason`
`OFF | PREREQUISITE_FAILED | RULE_MATCH(ruleId) | FALLTHROUGH | DEFAULT`. Sealed; clients pattern-match for diagnostics.

## 11. Composite — Rule + Conditions
A `Rule` is a list of conditions ANDed. The targeting list is conditions ORed. Composite tree of predicates.

## 12. Cached snapshot
CDN-cached `snapshot.json` per environment. SDKs bootstrap fast without hammering origin.

## What we explicitly avoid

| Pattern | Why not |
| --- | --- |
| Remote evaluation per request | Fails the <1ms latency requirement |
| Client-side rule parsing on every call | Pre-compile rules at load time; reuse |
| Sticky bucketing via DB lookup | Would break determinism + add latency; hash is the answer |
| String-typed operators | Strategy + enum-keyed map; fast and type-safe |
| Mutable Flag objects | Updates replace the whole flag atomically; readers never see torn state |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Operator | Pluggable condition operators |
| Strategy | Bucketing | Pluggable hash algorithm |
| Strategy | Subscriber | Pluggable transport for updates |
| Repository | FlagRepo, AuditRepo | DB abstraction |
| Builder | EvaluationContext | Optional attributes |
| Event sourcing | Audit log | Compliance + history |
| Pub/Sub | Kafka updates topic | Server → many SDK fan-out |
| Observer | FlagStore listeners | SDK-side reactions |
| Optimistic CAS | If-Match version | Concurrent admin edits |
| Discriminated union | EvaluationResult.reason | Exhaustive diagnostics |
| Cached snapshot | CDN per-env file | Fast SDK bootstrap |

## Output

```
The system is Strategy (operators, bucketing, transport) + Pub/Sub (updates) +
Repository + Optimistic CAS for admin writes + Cached snapshot for bootstrap.
The hot path is a pure function over an immutable Flag.
```
