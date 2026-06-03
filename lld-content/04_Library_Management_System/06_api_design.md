# 06 · Library — API Design

## Member APIs

### Search

```
GET /v1/books?q=algorithms&genre=cs&branch=br_1&cursor=...

200 OK
{
  "data": [
    {
      "id": "b_1",
      "title": "Introduction to Algorithms",
      "authors": ["Cormen","Leiserson"],
      "isbn": "9780262033848",
      "genres": ["cs","textbook"],
      "availability": {
        "branch_total": 3,
        "branch_available": 1,
        "system_total": 8,
        "system_available": 2
      }
    }
  ]
}
```

### Borrow

```
POST /v1/loans
Idempotency-Key: lo-1
{
  "member_id": "m_1",
  "book_id": "b_1",
  "preferred_branch_id": "br_1"
}

201 Created
{
  "data": {
    "id": "l_1",
    "copy_id": "c_42",
    "due_date": "2025-05-03",
    "branch_id": "br_1"
  }
}
```

Errors:
- `409 NO_COPY_AVAILABLE` — borrow elsewhere or reserve.
- `409 LIMIT_REACHED` — too many active loans.
- `409 OUTSTANDING_FINES` — pay first.
- `403 ACCOUNT_SUSPENDED`.

### Return

```
POST /v1/loans/{id}:return
{ "branch_id": "br_2" }

200 OK
{
  "data": {
    "id": "l_1",
    "status": "RETURNED",
    "fine": null,
    "transferred": true
  }
}
```

If returned at a different branch, `transferred=true` triggers a transfer task — the copy stays with the receiving branch.

### Renew

```
POST /v1/loans/{id}:renew
200 OK
{
  "data": { "id": "l_1", "due_date": "2025-05-10", "renewals": 1 }
}
```

Errors: `409 RENEWAL_LIMIT`, `409 RESERVATION_EXISTS`.

### Reserve

```
POST /v1/reservations
{
  "member_id": "m_1",
  "book_id": "b_1",
  "preferred_branch_id": "br_1"
}

201 Created
{
  "data": {
    "id": "r_1",
    "queue_position": 3,
    "status": "QUEUED"
  }
}
```

### Cancel reservation

```
POST /v1/reservations/{id}:cancel
```

### View loans / fines

```
GET /v1/me/loans?status=ACTIVE
GET /v1/me/fines?status=OUTSTANDING
```

### Pay fine

```
POST /v1/fines/{id}:pay
Idempotency-Key: pf-1
{ "payment_method_id": "pm_1" }
```

---

## Librarian APIs

### Catalog

```
POST   /v1/admin/books                     create book
PATCH  /v1/admin/books/{id}                update book
POST   /v1/admin/books/{id}/copies         add copy(s)
PATCH  /v1/admin/copies/{id}               edit copy (e.g. shelf)
POST   /v1/admin/copies/{id}:mark-lost
POST   /v1/admin/copies/{id}:mark-repair
POST   /v1/admin/copies/{id}:transfer      to another branch
```

### Operations

```
GET  /v1/admin/loans?overdue=true&branch=br_1
POST /v1/admin/loans                       issue loan manually (counter)
POST /v1/admin/loans/{id}:return-manual    librarian counter return
POST /v1/admin/fines/{id}:waive            { "reason": "Goodwill" }
GET  /v1/admin/reservations?branch=...
```

### Members

```
POST  /v1/admin/members                    create
POST  /v1/admin/members/{id}:suspend       { "reason": "..." }
POST  /v1/admin/members/{id}:reinstate
GET   /v1/admin/members/{id}/history
```

### Reports

```
GET /v1/admin/reports/top-borrowed?from=...&to=...
GET /v1/admin/reports/overdue-rate
GET /v1/admin/reports/fines-collected?from=...&to=...
```

---

## Errors

```
NO_COPY_AVAILABLE
LIMIT_REACHED
OUTSTANDING_FINES
ACCOUNT_SUSPENDED
RENEWAL_LIMIT
RESERVATION_EXISTS
ALREADY_RESERVED
NOT_RETURNABLE_HERE
```

---

## SLA

| Endpoint | p99 |
| --- | --- |
| Search | 200 ms |
| Borrow | 300 ms |
| Return | 300 ms |
| Reserve | 200 ms |
| Pay fine | 600 ms (gateway) |
