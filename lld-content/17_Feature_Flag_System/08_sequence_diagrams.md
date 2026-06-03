# 08 · Feature Flag System — Sequence Diagrams

## 1. Evaluation (the hot path)

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cli as FeatureFlagClient
    participant Store as FlagStore (in-mem)
    participant Eval as Evaluator
    participant Buc as Bucketing

    App->>Cli: isOn("show-checkout", ctx)
    Cli->>Store: get("show-checkout")
    Store-->>Cli: Flag (or null)
    alt flag missing
      Cli-->>App: defaultValue
    else
      Cli->>Eval: evaluate(flag, ctx)
      Eval->>Eval: enabled? → if not, off variation
      loop prereqs
        Eval->>Eval: evaluate(prereq, ctx) — fail returns off
      end
      loop targeting rules
        Eval->>Eval: rule.matches(ctx)?
        alt FIXED match
          Eval-->>Cli: variationId
        else PERCENTAGE match
          Eval->>Buc: bucket(flag.key, ctx.userId)
          Buc-->>Eval: 0..9999
          alt within rollout
            Eval-->>Cli: variationId
          end
        end
      end
      Eval-->>Cli: fallthrough variation
      Cli-->>App: value
    end
```

## 2. Admin update — write path

```mermaid
sequenceDiagram
    autonumber
    participant Adm as Admin
    participant API as Admin API
    participant DB as Postgres
    participant Aud as Audit
    participant Bus as Kafka
    participant Sub as SDK subscriber

    Adm->>API: PUT /flags/show-checkout (If-Match: v17)
    API->>API: validate payload (cycles, refs, percentages)
    API->>DB: SELECT version WHERE key, env (= 17)
    API->>DB: UPDATE flags SET ..., version=18
    API->>Aud: INSERT audit row (before, after)
    API->>Bus: publish FlagUpdated{ env, key, v=18, payload }
    API-->>Adm: 200 OK { version: 18 }
    Bus->>Sub: deliver FlagUpdated
    Sub->>Sub: applyAtomically(flag) → store.put(flag)
```

## 3. SDK bootstrap

```mermaid
sequenceDiagram
    autonumber
    participant SDK
    participant CDN
    participant SSE as Edge SSE
    participant Bus as Kafka

    SDK->>CDN: GET /sdk/v1/snapshots/prod (If-None-Match: "v100")
    alt cache hit
      CDN-->>SDK: 304 Not Modified
    else
      CDN-->>SDK: 200 { flags, version: 124 }
    end
    SDK->>SDK: load flags into FlagStore
    SDK->>SSE: GET /sdk/v1/stream/prod?since=124
    SSE->>Bus: subscribe (env=prod, since=v124)
    loop deltas
      Bus-->>SSE: FlagUpdated{ v=125 }
      SSE-->>SDK: event: flag-update
      SDK->>SDK: store.put(flag), store.version=125
    end
```

## 4. Optimistic concurrency conflict

```mermaid
sequenceDiagram
    autonumber
    participant A1 as Admin A
    participant A2 as Admin B
    participant API
    participant DB

    A1->>API: PUT (If-Match: v17)
    API->>DB: UPDATE WHERE version=17 → 1 row
    API-->>A1: 200, version: 18

    A2->>API: PUT (If-Match: v17)  [stale]
    API->>DB: UPDATE WHERE version=17 → 0 rows
    API-->>A2: 409 Conflict, current=18
    A2->>A2: re-fetch, re-edit, retry
```

## 5. Prerequisite chain

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant Cli
    participant Eval

    App->>Cli: isOn("checkout-v2", ctx)
    Cli->>Eval: evaluate("checkout-v2")
    Eval->>Eval: prereq: payments-online == 'on'?
    Eval->>Eval: evaluate("payments-online", ctx)
    alt payments-online = on
      Eval->>Eval: continue rules of checkout-v2
    else payments-online = off
      Eval-->>Cli: offVariation (PREREQUISITE_FAILED)
    end
    Cli-->>App: value
```

## 6. Sticky bucketing across rollout expansion

```mermaid
sequenceDiagram
    autonumber
    participant U as user "u-42"
    participant Cli

    Note over Cli: rollout = 10%
    U->>Cli: isOn("new-ui")
    Cli->>Cli: bucket = hash("new-ui:u-42") mod 10000 = 873
    Cli->>Cli: 873 < 1000 → "on"
    Cli-->>U: on

    Note over Cli: admin expands rollout to 50%
    U->>Cli: isOn("new-ui")
    Cli->>Cli: bucket = 873 (same)
    Cli->>Cli: 873 < 5000 → "on"   (still on)
    Cli-->>U: on

    Note over Cli: admin shrinks rollout back to 5%
    U->>Cli: isOn("new-ui")
    Cli->>Cli: bucket = 873
    Cli->>Cli: 873 < 500 → false   (now off)
    Cli-->>U: off
```

Subset semantics: expanding only adds users; shrinking only removes them.

## 7. SSE disconnect → fallback to polling

```mermaid
sequenceDiagram
    autonumber
    participant SDK
    participant SSE
    participant API as REST API

    SDK->>SSE: open
    Note over SDK: heartbeat timeout (no event in 30s)
    SDK->>SSE: close
    SDK->>API: GET /admin/envs/prod/flags?since=v125  [polling fallback]
    API-->>SDK: deltas
    SDK->>SSE: reconnect (with new since)
```

## Output

```
Hot path:        evaluate flag → prereqs → targeting rules → percentage bucket → fallthrough
Write path:      validate → optimistic UPDATE → audit → publish → SDKs apply patch
Bootstrap:       CDN snapshot (ETag) + SSE deltas
Concurrency:     If-Match version on PUT; SDK store updated atomically
Sticky:          hash(flag+userId); subset on expansion
Resilience:      SSE drops → poll until reconnect
```
