# 07 · API Design Guide

> The API contract is the **most stable** thing in your system. It outlives databases, languages, frameworks. Design it deliberately.

---

## Principles

```
1. Resources are nouns; HTTP verbs are actions.
2. Idempotency is mandatory for every mutating endpoint.
3. Errors are machine-readable, not strings.
4. Pagination is always cursor-based for unbounded lists.
5. Versioning lives in the URL path (or Accept header).
6. Time is always UTC and ISO-8601.
7. Money is never a float. Always minor units (cents) or string decimal.
8. Identifiers are opaque (UUID/ULID), not auto-increment integers.
9. Validation errors enumerate every offending field.
10. Logs/traces use a request_id propagated end-to-end.
```

---

## REST Verbs and Their Semantics

| Verb | Idempotent | Safe | Typical use |
| --- | --- | --- | --- |
| GET | yes | yes | Read |
| HEAD | yes | yes | Existence check |
| POST | no | no | Create |
| PUT | yes | no | Replace whole resource |
| PATCH | no¹ | no | Partial update |
| DELETE | yes | no | Remove |

¹ PATCH **can** be made idempotent with conditional headers; in practice treat it as not.

The interviewer cares that you know POST is **not** idempotent by default — and that's why we add idempotency keys.

---

## Resource URL Conventions

```
GET    /v1/orders                        list
POST   /v1/orders                        create
GET    /v1/orders/{id}                   read one
PATCH  /v1/orders/{id}                   partial update
DELETE /v1/orders/{id}                   delete (rare; prefer status)

POST   /v1/orders/{id}:cancel            action  (Google AIP convention)
GET    /v1/orders/{id}/items             sub-resource
GET    /v1/customers/{id}/orders         scoped list
```

### Action endpoints (verbs)

For non-CRUD actions — `cancel`, `refund`, `assign`, `confirm` — use `POST /resource/{id}:action`. Don't pretend it's REST when it isn't.

```
POST   /v1/rides/{id}:cancel
POST   /v1/orders/{id}:rate
POST   /v1/bookings/{id}:checkin
POST   /v1/groups/{id}:settle
```

### Plural vs singular

Always plural for collections. `/v1/orders`, never `/v1/order`.

### Lowercase, hyphens

`/v1/payment-methods`, not `/v1/PaymentMethods`. Lowercase, kebab-case.

---

## Request / Response Envelope

### Request

```json
POST /v1/orders
Headers:
  Authorization: Bearer <jwt>
  Idempotency-Key: ord-2025-04-19-abc123
  X-Request-Id: 7f2c9...
  Content-Type: application/json

Body:
{
  "customer_id": "u_a1b2",
  "restaurant_id": "r_55",
  "items": [
    { "menu_item_id": "m_9", "quantity": 2 }
  ],
  "delivery_address_id": "a_77"
}
```

### Response

```json
HTTP/1.1 201 Created
Headers:
  X-Request-Id: 7f2c9...
  Location: /v1/orders/ord_abc

Body:
{
  "data": {
    "id": "ord_abc",
    "status": "PLACED",
    "total_amount": "480.00",
    "currency": "INR",
    "estimated_delivery_at": "2025-04-19T20:30:00Z",
    "created_at": "2025-04-19T19:43:11Z"
  }
}
```

### Why wrap in `data`?

Future-proofing. You may need to add `meta`, `links`, `included`. JSON:API and Stripe both wrap. Pick a convention and stay consistent.

---

## Idempotency Keys — The Standard

### Rules

1. Client supplies a unique `Idempotency-Key` header for every mutating request.
2. Server stores `(idempotency_key, response_body, status_code)` for at least 24h.
3. Subsequent requests with the same key return the **stored response** — no re-execution.
4. If payload differs for the same key → reject with 409.

### Server pattern

```java
public ResponseEntity<?> placeOrder(@RequestHeader("Idempotency-Key") String key,
                                    @RequestBody PlaceOrderRequest req) {
  Optional<StoredResponse> hit = idemRepo.find(key);
  if (hit.isPresent()) {
    if (!hit.get().payloadHash().equals(hash(req))) {
      return ResponseEntity.status(409)
        .body(error("IDEMPOTENCY_PAYLOAD_MISMATCH", "Same key, different payload"));
    }
    return ResponseEntity.status(hit.get().statusCode())
                         .body(hit.get().body());
  }
  Order created = orderService.placeOrder(req);
  StoredResponse stored = StoredResponse.of(201, created, hash(req));
  idemRepo.save(key, stored);                    // UNIQUE constraint enforces at-most-once
  return ResponseEntity.status(201).body(stored.body());
}
```

### Storage

```sql
CREATE TABLE idempotency_records (
  key            VARCHAR(80) PRIMARY KEY,
  status_code    INT,
  response_body  JSONB,
  payload_hash   CHAR(64),                       -- sha-256 of request
  created_at     TIMESTAMPTZ NOT NULL,
  resource_type  VARCHAR(50),
  resource_id    UUID
);

CREATE INDEX idx_idempotency_created_at ON idempotency_records (created_at);
```

TTL via background job: delete rows older than 24–48h.

### Key generation

- **Client** generates the key (UUID or hash of inputs + nonce).
- Some libraries (Stripe SDK) generate keys per-request automatically when retrying.

---

## Pagination

### Don't: offset/limit

```
GET /v1/orders?offset=10000&limit=20
```

- Slow on large tables (DB still scans the offset).
- Inconsistent if items are inserted/deleted between calls.

### Do: cursor-based

```
GET /v1/orders?cursor=eyJjcmVhdGVkX2F0Ijoi...&limit=20

Response:
{
  "data": [...20 items...],
  "next_cursor": "eyJjcmVhdGVkX2F0Ijoi..."
}
```

The cursor is an **opaque, signed** encoding of the last item's sort key (e.g., `(created_at, id)`). The DB query becomes:

```sql
SELECT * FROM orders
WHERE (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT ?;
```

This uses the index efficiently, regardless of depth.

### Why opaque?

So you can change the underlying sort key without breaking clients. Encode `(created_at, id)` today; switch to `(updated_at, id)` later.

---

## Error Modeling

### Bad

```json
{ "error": "User not found" }
```

Clients have to parse strings. They can't build retry/error UI logic.

### Good

```json
HTTP/1.1 404 Not Found
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User u_xyz does not exist",
    "request_id": "7f2c9...",
    "details": {
      "user_id": "u_xyz"
    }
  }
}
```

### Error code namespace

```
INVALID_ARGUMENT
UNAUTHENTICATED
PERMISSION_DENIED
NOT_FOUND
ALREADY_EXISTS
RESOURCE_EXHAUSTED        ← rate limit, quota
FAILED_PRECONDITION       ← state machine violation
ABORTED                   ← optimistic lock conflict
OUT_OF_RANGE
UNIMPLEMENTED
INTERNAL
UNAVAILABLE               ← retryable
DATA_LOSS
DEADLINE_EXCEEDED         ← timeout
```

These are gRPC's canonical codes. Map them to HTTP status:

| Code | HTTP |
| --- | --- |
| INVALID_ARGUMENT | 400 |
| UNAUTHENTICATED | 401 |
| PERMISSION_DENIED | 403 |
| NOT_FOUND | 404 |
| ALREADY_EXISTS | 409 |
| FAILED_PRECONDITION | 422 |
| RESOURCE_EXHAUSTED | 429 |
| ABORTED | 409 |
| INTERNAL | 500 |
| UNAVAILABLE | 503 |
| DEADLINE_EXCEEDED | 504 |

### Field-level validation

```json
HTTP/1.1 400 Bad Request
{
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "Validation failed for 2 fields",
    "fields": [
      { "field": "items[0].quantity", "code": "MIN", "message": "Must be >= 1" },
      { "field": "delivery_address_id", "code": "REQUIRED", "message": "Required" }
    ]
  }
}
```

---

## Versioning

### URL path versioning (preferred for public APIs)

```
/v1/orders   /v2/orders
```

- Clear, cache-friendly.
- Easy to route in load balancers.

### Accept header versioning

```
Accept: application/vnd.company.orders.v2+json
```

- Cleaner URLs.
- Harder to debug.

Pick **one** and document it. Internal-only services often skip versioning entirely and use deploy-time coordination.

### Compatibility rules

- **Additive changes** (new field, new endpoint): no version bump.
- **Removing or renaming a field**: version bump.
- **Changing semantics of a field**: version bump.

> Backward compatibility is a feature you sell. Break it once and clients lose trust.

---

## Authentication & Authorization

### Auth options

| Use case | Auth |
| --- | --- |
| Public B2C app | OAuth 2.0 / OIDC, short-lived JWT |
| Service-to-service | mTLS or JWT signed by service mesh |
| Admin / internal | OAuth + RBAC |
| Partner | API keys (rotated quarterly) + IP allowlist |
| Webhooks | HMAC signature in header (`X-Signature: ...`) |

### Authorization

Distinguish **authentication** (who) from **authorization** (what).

```
Customer u_123 can:
  - GET  /v1/orders?customer_id=u_123    ✓
  - GET  /v1/orders?customer_id=u_456    ✗  (403)
  - POST /v1/admin/...                    ✗  (403)
```

Encode in code:

```java
public Order get(UUID orderId, AuthCtx auth) {
  Order o = orderRepo.findById(orderId).orElseThrow(NotFound::new);
  if (!auth.userId().equals(o.customerId()) && !auth.hasRole("ADMIN"))
    throw new ForbiddenException();
  return o;
}
```

In real systems, use a policy engine (OPA, Cerbos) or a centralized authorizer.

---

## Rate Limiting

### Why

Protects you from:
- Buggy clients
- Malicious clients
- Resource exhaustion under burst traffic

### Schemes

| Algorithm | Use |
| --- | --- |
| Fixed window | Simple but bursty |
| Sliding window log | Accurate but expensive |
| Sliding window counter | Most common, balanced |
| Token bucket | Smoother bursts |
| Leaky bucket | Steady throughput |

Token bucket is the staff-level default.

### Response

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1714131000
Retry-After: 30

{
  "error": { "code": "RESOURCE_EXHAUSTED", "message": "Rate limit exceeded" }
}
```

### Where to enforce

- **Edge** (API gateway / CDN) — coarse.
- **Service** — per-tenant quotas.
- **DB / external service** — circuit breaker.

---

## Time, Money, IDs — Standard Pitfalls

### Time

- Always UTC, ISO-8601: `2025-04-19T20:30:00Z`.
- Never assume client and server clocks agree.
- Server is the source of truth for timestamps.

### Money

```json
"amount": "480.00",
"currency": "INR"
```

- Use string decimal or minor units (`48000` for cents).
- Never `float`. Floating-point math will haunt you (`0.1 + 0.2 != 0.3`).
- Always include currency.

### IDs

- Use opaque IDs (UUID, ULID, KSUID).
- Don't expose auto-increment integers (info leak: `?order=1235` → "we have ~1235 orders").
- Prefix with type (`u_`, `ord_`, `r_`) for human readability.

---

## Documentation

OpenAPI / Swagger is the standard. In an interview, you don't need to write YAML, but say:

> "I'd document this with OpenAPI, generate client SDKs, and run a contract test in CI to prevent breaking changes."

---

## Webhooks (External Events)

### Pattern

You send: `POST https://customer.example.com/webhooks/orders`
With:
```
X-Signature: sha256=hex(hmac(secret, body))
X-Event: order.placed
```

The customer:
1. Verifies the signature.
2. ACKs with 200.
3. If you don't get 200, retry with exponential backoff up to N times.
4. Eventually move to dead-letter queue and alert.

### Why HMAC

Prevents an attacker from forging events. Don't ship webhooks without signing.

---

## Streaming / Long-running Endpoints

| Use | Choice |
| --- | --- |
| Live driver/order updates | WebSocket / SSE |
| File upload | Resumable upload protocol (TUS, gRPC streams) |
| Long jobs | Submit job → poll status → download result |

For long jobs:

```
POST   /v1/jobs/refund-batch          → 202 Accepted, returns {job_id, status: "PENDING"}
GET    /v1/jobs/{id}                  → status + result_url when done
```

Don't make a synchronous endpoint that takes 30 seconds.

---

## Real Example — Splitwise Add Expense

### Request

```http
POST /v1/groups/{group_id}/expenses
Idempotency-Key: exp-uuid-1
Content-Type: application/json

{
  "description": "Dinner",
  "amount": "1200.00",
  "currency": "INR",
  "paid_by": [{ "user_id": "u_1", "amount": "1200.00" }],
  "split_strategy": "EQUAL",
  "shares": [
    {"user_id": "u_1"},
    {"user_id": "u_2"},
    {"user_id": "u_3"}
  ]
}
```

### Response

```http
201 Created

{
  "data": {
    "id": "exp_xyz",
    "amount": "1200.00",
    "currency": "INR",
    "split_strategy": "EQUAL",
    "splits": [
      {"user_id": "u_1", "amount": "400.00"},
      {"user_id": "u_2", "amount": "400.00"},
      {"user_id": "u_3", "amount": "400.00"}
    ],
    "created_at": "2025-04-19T19:43:11Z"
  }
}
```

### Errors

| Code | When |
| --- | --- |
| `INVALID_ARGUMENT` | bad split, negative amount, unknown user |
| `NOT_FOUND` | group does not exist |
| `PERMISSION_DENIED` | user not in group |
| `IDEMPOTENCY_PAYLOAD_MISMATCH` | same key, different body |
| `RESOURCE_EXHAUSTED` | rate limit |

---

## Checklist

- [ ] All mutating endpoints accept `Idempotency-Key`.
- [ ] All errors have `code`, `message`, `request_id`.
- [ ] Pagination is cursor-based.
- [ ] Time is UTC ISO-8601; money has currency; IDs are opaque.
- [ ] Validation errors list each offending field.
- [ ] Webhooks are HMAC-signed.
- [ ] Rate limit headers are returned.
- [ ] Versioning policy stated.
