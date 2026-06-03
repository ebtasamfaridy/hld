# 03 · Feature Flag System — High-Level Design

## Architecture

```mermaid
flowchart LR
    subgraph "Admin plane"
      UI[Admin UI] --> A[Admin API]
      A -- writes --> DB[(Postgres: flags, rules, env, audit)]
      A -- publish --> Bus[Update Bus<br/>Kafka / Redis Streams]
    end

    subgraph "Data plane"
      Bus --> Edge[Edge / SSE Server]
      Edge -- SSE/WS --> S1[SDK in App Server 1]
      Edge -- SSE/WS --> S2[SDK in App Server 2]
      S1 -->|"isOn(key, ctx)"| Eval1[Local Evaluator]
      S2 --> Eval2[Local Evaluator]
    end

    subgraph "Bootstrap"
      S1 -- initial fetch --> CDN[CDN-cached snapshot]
      S2 --> CDN
      CDN --- DB
    end
```

## Roles

| Component | Responsibility |
| --- | --- |
| **Admin API** | CRUD on flags + rules + envs; validate; persist; publish update |
| **Postgres** | Source of truth for flag config + audit log |
| **Update Bus** | Kafka or Redis Streams; carries flag-change events |
| **Edge / SSE Server** | Maintains long-lived connections to SDKs; broadcasts updates |
| **SDK** | Pulls initial snapshot; subscribes to updates; evaluates locally |
| **CDN snapshot** | Static-ish daily/hourly snapshot of flag config; bootstraps cold SDKs |

## Hot paths

### Read path (the only one that matters for performance)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant SDK as Local SDK
    participant Eval as Evaluator

    App->>SDK: isOn("show-new-checkout", ctx)
    SDK->>Eval: evaluate flag for ctx
    Eval->>Eval: check prerequisites
    Eval->>Eval: walk targeting rules — first match wins
    Eval->>Eval: if percentage rule: hash(flag+user) mod 10000 < N
    Eval-->>SDK: variation
    SDK-->>App: variation
```

Sub-microsecond. No network.

### Write path (admin)

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant API as Admin API
    participant DB
    participant Aud as Audit log
    participant Bus
    participant SDK

    Admin->>API: PUT /flags/{key} (env=prod, rules=...)
    API->>API: validate rules
    API->>DB: SELECT version (optimistic check)
    API->>DB: UPDATE flags, version+1
    API->>Aud: INSERT audit row
    API->>Bus: publish FlagUpdated{key, env, version, payload}
    API-->>Admin: 200 OK
    Bus->>SDK: broadcast (SSE)
    SDK->>SDK: apply patch atomically
```

### SDK bootstrap

1. SDK starts; fetches `/sdk/v1/flags?env=prod&etag=...` from CDN.
2. Loads config into memory; sets a flag-store version.
3. Connects to SSE: `/sdk/v1/stream?env=prod&since=version`.
4. Receives any catch-up events; subscribes to live stream.

This is **the** pattern: bulk pull + delta stream.

## Flag evaluation algorithm

```
function evaluate(flag, context, default):
    if flag is OFF:                       return flag.offVariation
    for prereq in flag.prerequisites:
        if not evaluate(prereq, context, prereq.default).matches(prereq.expected):
            return flag.offVariation
    for rule in flag.targetingRules:      # ordered, first match wins
        if rule.matches(context):
            if rule.kind == FIXED:
                return rule.variation
            if rule.kind == PERCENTAGE:
                bucket = hash(flag.key + ":" + context.userId) mod 10000
                if bucket < rule.percentage * 100:
                    return rule.variation
                else:
                    continue   # rule didn't apply for this user
    return flag.fallthroughVariation     # the "everyone else" default
```

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Bus down | SDK evaluates with last known config; alert; no eval errors |
| DB down | Admin API serves reads from Redis cache; writes fail fast |
| Edge / SSE down | SDK falls back to long polling |
| Network split | SDK keeps last config; eventually reconnects and catches up |
| Flag doesn't exist on SDK | Return default (passed by caller) |
| SDK can't even reach CDN snapshot at boot | Use bundled defaults; alert |

## Output

```
Plane split:   Admin (Postgres truth + Bus publish) | Data (Edge + SDK local eval)
Hot path:      isOn = in-process function call (<1µs)
Cold path:     CDN snapshot for SDK bootstrap; SSE for live updates
Algorithm:     prereqs → targeting rules (first match) → fallthrough
Bucketing:     hash(flag + userId) mod 10000; subset-preserving
Failure:       SDK is always functional with cached config; fail safe to default
```
