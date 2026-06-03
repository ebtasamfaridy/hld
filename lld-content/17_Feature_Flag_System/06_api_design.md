# 06 · Feature Flag System — API Design

## Admin API (REST)

### Workspaces & Environments
```
POST   /admin/workspaces                  { name }
GET    /admin/workspaces

POST   /admin/workspaces/{w}/envs         { name }     # dev / staging / prod
GET    /admin/workspaces/{w}/envs
```

### Flags
```
GET    /admin/envs/{env}/flags                              list
POST   /admin/envs/{env}/flags                              create
GET    /admin/envs/{env}/flags/{key}                        details
PUT    /admin/envs/{env}/flags/{key}        body, ETag        update (If-Match)
PATCH  /admin/envs/{env}/flags/{key}/state  {enabled:false}   kill switch
DELETE /admin/envs/{env}/flags/{key}                          archive
GET    /admin/envs/{env}/flags/{key}/audit                    audit history
POST   /admin/envs/{env}/flags/{key}/restore                  un-archive
```

### Concurrency
- `If-Match: <version>` required on `PUT`. Server compares to current; rejects with 409 if stale.
- Optimistic. Admins see fresh version on UI refresh.

### Validation rules
- Variation IDs unique within a flag.
- `fallthroughVariationId` and `offVariationId` must reference existing variations.
- Targeting rules' `variationId` (FIXED) must reference existing variation.
- Percentage in 0..100.
- Prerequisite cycles rejected.

## SDK API (REST + SSE)

### Bootstrap snapshot
```
GET /sdk/v1/snapshots/{envId}                    Headers: If-None-Match: "v123"
                                                 → 200 { flags: [...], version: 124 } | 304
```

### Live stream
```
GET /sdk/v1/stream/{envId}?since=124              SSE
event: flag-update
data: { flagKey, version, payload }

event: flag-archived
data: { flagKey }

event: heartbeat
data: { ts }
```

Server sends a heartbeat every 15 s so the SDK can detect a dead connection.

## SDK Library API

```java
public interface FeatureFlags {
    boolean isOn(String key, EvaluationContext ctx);
    boolean isOn(String key, EvaluationContext ctx, boolean defaultValue);
    String  variation(String key, EvaluationContext ctx, String defaultValue);
    JsonNode jsonVariation(String key, EvaluationContext ctx, JsonNode defaultValue);

    EvaluationResult evaluate(String key, EvaluationContext ctx);  // includes reason

    void close();
}

public final class EvaluationContext {
    private final String userId;
    private final Map<String, Object> attributes;
    public static Builder builder(String userId);
}
```

Note the `default` parameter on every call — if the flag is unknown or service is unreachable, the SDK returns the default. The SDK never throws.

## Errors

| Code | Meaning | Caller |
| --- | --- | --- |
| 404 | Flag not found | Caller's default returned (in SDK). 404 surfaced from admin API. |
| 409 | Version mismatch on PUT | UI refreshes, retries |
| 422 | Validation failed (cycle, missing variation) | Fix payload |
| 401/403 | Auth | n/a |
| 5xx | Backend error | Retry with backoff |

## Audit + observability

```
GET    /admin/envs/{env}/flags/{key}/audit?cursor=...
GET    /admin/envs/{env}/metrics?key=show-checkout
       → { evaluations_total, hits, misses, by_variation }
```

Metrics are emitted by SDKs back to the platform (sampled). Useful for understanding rollout impact.

## Rate limiting

```
SDK fetches:    1000 req/s per SDK key, low cost (CDN-cached)
Admin writes:   60/min per actor, per environment
```

## Output

```
Admin REST:  CRUD on flags + envs + audit; If-Match concurrency; strict validation
SDK API:     bootstrap snapshot (CDN, ETag) + SSE stream for deltas
SDK lib:     isOn / variation / jsonVariation, all with defaults; never throws
Errors:      404 / 409 / 422; SDKs degrade gracefully; admins see typed errors
```
