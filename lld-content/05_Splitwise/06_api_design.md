# 06 · Splitwise — API Design

## User APIs

### Auth + profile

```
POST /v1/auth/signup
POST /v1/auth/login
GET  /v1/me
PATCH /v1/me   { "home_currency": "USD" }
```

### Friends

```
POST /v1/friends     { "email": "alice@example.com" }
GET  /v1/friends
DELETE /v1/friends/{userId}      // only if zero balance
```

### Groups

```
POST /v1/groups
{
  "name": "Goa Trip",
  "type": "TRIP",
  "member_ids": ["u_1","u_2","u_3"]
}

GET    /v1/groups
GET    /v1/groups/{id}
PATCH  /v1/groups/{id}   { "name": "Goa 2025" }
POST   /v1/groups/{id}/members      { "user_id": "u_4" }
DELETE /v1/groups/{id}/members/{userId}    // only if zero balance with everyone
POST   /v1/groups/{id}:close
```

### Expenses

```
POST /v1/expenses
Idempotency-Key: exp-2025-04-19-abc

{
  "group_id": "g_1",            // null for non-group
  "description": "Goa hotel",
  "amount": "12000.00",
  "currency": "INR",
  "occurred_at": "2025-04-19T20:00:00Z",
  "split_method": "EQUAL",
  "payers": [
    { "user_id": "u_1", "amount": "12000.00" }
  ],
  "participants": ["u_1","u_2","u_3"]
}

201 Created
{
  "data": {
    "id": "e_1",
    "shares": [
      { "user_id": "u_1", "owed_amount": "4000.00" },
      { "user_id": "u_2", "owed_amount": "4000.00" },
      { "user_id": "u_3", "owed_amount": "4000.00" }
    ]
  }
}
```

For other split methods, the request payload differs:

```
EXACT:
{
  "split_method": "EXACT",
  "shares": [
    { "user_id": "u_1", "owed_amount": "5000.00" },
    { "user_id": "u_2", "owed_amount": "4000.00" },
    { "user_id": "u_3", "owed_amount": "3000.00" }
  ]
}

PERCENT:
{
  "split_method": "PERCENT",
  "shares": [
    { "user_id": "u_1", "percent": "50" },
    { "user_id": "u_2", "percent": "30" },
    { "user_id": "u_3", "percent": "20" }
  ]
}

SHARE:
{
  "split_method": "SHARE",
  "shares": [
    { "user_id": "u_1", "shares": 2 },
    { "user_id": "u_2", "shares": 1 },
    { "user_id": "u_3", "shares": 1 }
  ]
}

ITEM_WISE:
{
  "split_method": "ITEM_WISE",
  "items": [
    { "name": "Pizza", "amount": "1500.00", "consumers": ["u_1","u_2"] },
    { "name": "Beer",  "amount": "800.00",  "consumers": ["u_1","u_3"] }
  ]
}

ADJUSTMENT:
{
  "split_method": "ADJUSTMENT",
  "shares": [
    { "user_id": "u_1", "adjustment": "0.00" },
    { "user_id": "u_2", "adjustment": "100.00" },   // owes 100 more
    { "user_id": "u_3", "adjustment": "-50.00" }    // owes 50 less
  ]
}
```

Server validates and computes final shares.

### Edit / delete

```
PATCH /v1/expenses/{id}
DELETE /v1/expenses/{id}
```

Both operations are version-controlled via optimistic locking.

### Get / list

```
GET /v1/expenses/{id}
GET /v1/expenses?group=g_1&cursor=...&limit=50
```

### Settlements

```
POST /v1/settlements
Idempotency-Key: settle-1

{
  "payer_id": "u_2",
  "payee_id": "u_1",
  "group_id": "g_1",
  "amount": "1000.00",
  "currency": "INR",
  "method": "UPI",
  "settled_at": "2025-04-19T22:00:00Z"
}

201
```

```
POST /v1/settlements/{id}:reverse
POST /v1/settlements/{id}:dispute
```

---

## Balance APIs

### Per-friend

```
GET /v1/balances/friend/{user_id}

200 OK
{
  "data": {
    "by_currency": [
      { "currency": "INR", "net_amount": "1500.00" },
      { "currency": "USD", "net_amount": "-100.00" }
    ]
  }
}
```

Positive = friend owes me; negative = I owe friend.

### Per-group

```
GET /v1/balances/group/{group_id}

200 OK
{
  "data": {
    "by_member": {
      "u_1": [{ "currency": "INR", "net_amount": "+8000" }],
      "u_2": [{ "currency": "INR", "net_amount": "-3000" }],
      "u_3": [{ "currency": "INR", "net_amount": "-5000" }]
    }
  }
}
```

### Overall

```
GET /v1/balances/overall

200 OK
{
  "data": {
    "by_currency": [
      { "currency": "INR", "you_owe": "200", "owed_to_you": "1700", "net": "1500" }
    ]
  }
}
```

### Debt simplification

```
GET /v1/balances/group/{group_id}/simplify

200 OK
{
  "data": {
    "transfers": [
      { "from": "u_2", "to": "u_1", "amount": "3000.00", "currency": "INR" },
      { "from": "u_3", "to": "u_1", "amount": "5000.00", "currency": "INR" }
    ]
  }
}
```

Suggestions only; user records actual settlements when they happen.

---

## Activity feed

```
GET /v1/activity?cursor=...&limit=20

200 OK
{
  "data": [
    { "type": "EXPENSE_CREATED", "expense_id": "e_1", "by": "u_1", "summary": "Goa hotel · ₹12000", "at": "..." },
    { "type": "SETTLEMENT_RECORDED", "settlement_id": "s_1", "summary": "u_2 paid u_1 ₹1000", "at": "..." }
  ],
  "next_cursor": "..."
}
```

---

## Errors

```
INVALID_ARGUMENT           // bad fields
INVALID_SPLIT              // sum mismatch
PARTICIPANT_NOT_IN_GROUP
GROUP_CLOSED
USER_NOT_FRIEND
HAS_NONZERO_BALANCE        // can't remove member
IDEMPOTENCY_PAYLOAD_MISMATCH
```

---

## SLA

| Endpoint | p99 |
| --- | --- |
| Create expense | 200 ms |
| Read balance | 100 ms (cached) |
| Debt simplify | 300 ms |
| Activity feed | 200 ms |
| Settlement | 200 ms |
