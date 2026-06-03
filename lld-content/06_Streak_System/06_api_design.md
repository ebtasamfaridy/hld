# 06 · Streak System — API Design

REST + JSON. Internal services may also consume Kafka topics directly.

## Conventions

- Base: `https://api.example.com/v1`
- Auth: bearer JWT (claims: `sub` = userId, `roles` = [user|admin]).
- Errors: `application/problem+json`.
- Idempotency: `Idempotency-Key` header on writes.
- Time: all timestamps `ISO-8601 UTC`. Calendar uses `YYYY-MM-DD` in user TZ.
- Versioning: URI prefix.

## User endpoints

### `GET /v1/me/streak`

Returns the user's current streak under the **active streak type** (admin-configured).

**200 OK**
```json
{
  "type": "APP_VISIT",
  "current": 12,
  "longest": 47,
  "last_active_day": "2025-06-13",
  "today": "2025-06-14",
  "is_alive": true,
  "timezone": "Asia/Kolkata"
}
```

`is_alive=false` means: the streak counter is paused — the user must engage today (or it's already too late).

### `GET /v1/me/streak?type=LISTENING`

Override the active type to query a specific streak type. Useful for power users / debugging.

### `GET /v1/me/streak/calendar?year=2025&month=6&type=APP_VISIT`

**200 OK**
```json
{
  "year": 2025,
  "month": 6,
  "type": "APP_VISIT",
  "timezone": "Asia/Kolkata",
  "days": [
    { "day": "2025-06-01", "active": true,  "event_count": 4 },
    { "day": "2025-06-02", "active": true,  "event_count": 1 },
    { "day": "2025-06-03", "active": false, "event_count": 0 },
    ...
  ],
  "current_streak": 12,
  "month_active_days": 18
}
```

`days` always has `daysInMonth` entries for a stable client UI.

### `GET /v1/me/milestones`

```json
{
  "milestones": [
    { "type": "APP_VISIT", "days": 7,   "achieved_at": "2025-04-12T09:01:23Z" },
    { "type": "APP_VISIT", "days": 30,  "achieved_at": "2025-05-05T08:30:12Z" }
  ],
  "next": [
    { "type": "APP_VISIT", "days": 100, "remaining": 25 }
  ]
}
```

### `POST /v1/me/activity` (rare — most events come via Kafka)

When the client wants to push an explicit "I'm here" ping (e.g., `App Open`):

**Request**
```http
POST /v1/me/activity
Idempotency-Key: 7c2d-...-a1
Content-Type: application/json

{
  "type": "APP_VISIT",
  "occurred_at": "2025-06-14T17:42:01+05:30",
  "timezone": "Asia/Kolkata",
  "event_id": "evt-9f...c4"
}
```

**202 Accepted**
```json
{ "status": "queued", "event_day": "2025-06-14" }
```

We respond `202` because processing happens asynchronously through the Kafka pipeline; the client doesn't block on streak state. Reads remain consistent because the cache is updated synchronously before ack.

> Decision: V1 favors **server-side classification** — the client always reports raw activity. If the client says `type: LISTENING`, the server still re-classifies via the configured rules to prevent abuse.

## Admin endpoints

### `GET /v1/admin/streak-config`

```json
{
  "active_streak_type": "APP_VISIT",
  "milestones": [7, 30, 100, 365],
  "version": 12,
  "updated_at": "2025-06-10T11:22:00Z"
}
```

### `PATCH /v1/admin/streak-config`

```http
PATCH /v1/admin/streak-config
If-Match: "12"
Content-Type: application/json

{ "active_streak_type": "LISTENING" }
```

`If-Match` is the version number — server CAS prevents lost updates between admins.

**200 OK** with new config; **409 Conflict** if version mismatch.

### `POST /v1/admin/streak-config/milestones`

```json
{ "days": 50 }
```

Adds 50-day milestone.

### `POST /v1/admin/users/{user_id}/streak/adjust`

Compensation API for outages.

```json
{
  "type": "APP_VISIT",
  "operation": "GRANT_DAY",
  "day": "2025-06-13",
  "reason": "Outage 2025-06-13 14:00-16:00"
}
```

`GRANT_DAY` inserts a `daily_activity` row and recomputes `streak_state` for that user (re-runs the deterministic algorithm over the affected window). Other operations: `RESET`, `SET_CURRENT`.

### `GET /v1/admin/metrics/streaks`

Aggregate KPIs (daily): users on a streak ≥ N, distribution histogram, churn-out (broken today).

## Internal API (between services)

### Kafka topic `app.events` (consumed)

```json
{
  "event_id": "evt-9f...c4",
  "user_id": "user-123",
  "kind": "EPISODE_PLAYED",
  "metadata": { "episode_id": "...", "duration_seconds": 187 },
  "occurred_at": "2025-06-14T17:42:01Z",
  "user_timezone": "Asia/Kolkata"
}
```

Schema-registry-managed (Avro / Protobuf in real life).

### Kafka topic `streak.events` (produced)

```json
{ "kind": "STREAK_ADVANCED",
  "user_id": "user-123",
  "type": "APP_VISIT",
  "previous": 11, "current": 12, "longest": 47,
  "occurred_at": "..." }

{ "kind": "STREAK_BROKEN", ... }
{ "kind": "STREAK_MILESTONE_REACHED", "milestone_days": 30, ... }
{ "kind": "ADMIN_CONFIG_CHANGED", "old": "APP_VISIT", "new": "LISTENING" }
```

Consumed by Notification, Metrics ETL, audit log.

## Idempotency

| Endpoint | Idempotency mechanism |
| --- | --- |
| `POST /v1/me/activity` | `Idempotency-Key` header (24h) + `(user, type, day)` natural key |
| Kafka consumer | `event_id` dedup table OR `(user, type, day)` upsert |
| `PATCH /v1/admin/streak-config` | `If-Match` ETag (version) |
| `POST /v1/admin/users/.../streak/adjust` | request idempotency-key |

The activity endpoint is the only client-facing high-volume write. Dedup window = 24h is enough — same client retrying after that is treated as a fresh ping for the next day.

## Pagination & filtering

- Calendar API takes `year` + `month`; no pagination needed (max 31 rows).
- Milestone list is small; no pagination.
- Admin metrics use cursor pagination over user buckets.

## Error model

```json
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/streak/version-conflict",
  "title": "Version mismatch",
  "status": 409,
  "detail": "admin_config has been updated. Re-fetch and retry.",
  "current_version": 13
}
```

Standard problem-details. Error codes mirror domain failure types.

## Rate limiting

Per-user activity write: **60 / minute** (we don't expect more than 5/day in normal use).
Per-user reads: **100 / minute**.
Admin: **30 / minute**.

The Kafka path bypasses HTTP rate limiting (back-pressure handled by consumer scaling).

## Caching headers

- `GET /v1/me/streak`: `Cache-Control: private, max-age=60` (client may cache a minute).
- Calendar: `Cache-Control: private, max-age=300, stale-while-revalidate=300` (5 min).
- Admin config: `ETag` based on version; `Cache-Control: private, max-age=10`.

## Observability

Each request carries:
- `X-Request-Id`,
- `traceparent` (W3C trace context).

Custom metrics:
- `streak.activity.recorded` (counter, type, is_first_of_day)
- `streak.update.cas_failed` (counter)
- `streak.read.cache_hit_ratio` (gauge)
- `streak.calendar.read.latency_ms` (histogram)
- `streak.milestone.fired` (counter, milestone_days)
- `streak.admin.config_changed` (counter)

Alerts:
- `cas_failed` rate > 5 % → multi-device contention investigate.
- `activity.lag` > 30 s → consumer scaling issue.

## Output

```
User APIs:    streak read, calendar read, optional activity push, milestones
Admin APIs:   active-type toggle (CAS), milestones CRUD, user adjust, metrics
Internal:     Kafka in (app.events), Kafka out (streak.events)
Idempotency:  header + natural key + version
```
