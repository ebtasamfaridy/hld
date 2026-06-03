# 05 · BookMyShow — Database Design

## Catalog (Postgres)

```sql
CREATE TABLE movies (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    language TEXT,
    duration_min INT,
    rating TEXT,
    poster_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE theatres (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    city TEXT NOT NULL,
    geo POINT,
    address TEXT
);

CREATE INDEX idx_theatres_city ON theatres (city);

CREATE TABLE screens (
    id UUID PRIMARY KEY,
    theatre_id UUID NOT NULL REFERENCES theatres(id),
    name TEXT,
    rows INT, cols INT
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    screen_id UUID NOT NULL REFERENCES screens(id),
    row_label TEXT,
    col_no INT,
    category TEXT NOT NULL,        -- RECLINER | PREMIUM | STANDARD
    UNIQUE (screen_id, row_label, col_no)
);

CREATE TABLE shows (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movies(id),
    screen_id UUID NOT NULL REFERENCES screens(id),
    starts_at TIMESTAMPTZ NOT NULL,
    pricing JSONB NOT NULL,         -- frozen pricing snapshot for THIS show
    status TEXT NOT NULL,           -- SCHEDULED | OPEN | RUNNING | DONE | CANCELLED
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shows_starts_at ON shows (starts_at);
```

## Booking (Postgres)

```sql
CREATE TABLE holds (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    show_id UUID NOT NULL REFERENCES shows(id),
    seat_ids UUID[] NOT NULL,
    quote_amount_minor BIGINT NOT NULL,
    quote_breakdown JSONB NOT NULL,
    status TEXT NOT NULL,            -- HELD | CONFIRMED | EXPIRED | CANCELLED
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_booking_id UUID NULL
);

CREATE INDEX idx_holds_show_active ON holds (show_id) WHERE status = 'HELD';
CREATE INDEX idx_holds_user        ON holds (user_id, created_at DESC);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    show_id UUID NOT NULL REFERENCES shows(id),
    hold_id UUID NULL REFERENCES holds(id),
    total_amount_minor BIGINT NOT NULL,
    payment_ref TEXT NOT NULL,
    status TEXT NOT NULL,            -- CONFIRMED | CANCELLED | REFUNDED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
) PARTITION BY RANGE (created_at);

CREATE TABLE booking_seats (
    show_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    category TEXT NOT NULL,
    price_minor BIGINT NOT NULL,
    PRIMARY KEY (show_id, seat_id)               -- the no-double-book guard
);

CREATE INDEX idx_booking_seats_booking ON booking_seats (booking_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID UNIQUE REFERENCES bookings(id),
    gateway TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL,            -- AUTHORIZED | CAPTURED | REFUNDED | FAILED
    gateway_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (gateway, idempotency_key)
);
```

The `PRIMARY KEY (show_id, seat_id)` on `booking_seats` is the **single most important constraint** in this schema. Even if Redis somehow leaks two holds for the same seat, only one `INSERT` will succeed.

## Redis schema

```
hold:{showId}:{seatId}        STRING value=userId, EX=600          # 10 min TTL
seats:available:{showId}      SET of seatIds (cached)
seats:held:{showId}            SET of seatIds (derived; for fast layout reads)
layout:{showId}:v{version}     STRING (compressed JSON of full layout)  # versioned
```

The hold key serves both as the lock and the timer. Reading `EXISTS hold:{showId}:{seatId}` tells us a seat is held without joining tables.

## Audit / events

```sql
CREATE TABLE booking_events (
    id BIGSERIAL,
    booking_id UUID,
    show_id UUID NOT NULL,
    kind TEXT NOT NULL,
    payload JSONB,
    ts TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (ts);

-- Outbox for Kafka publishing
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id UUID,
    topic TEXT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_unpub ON outbox (published, created_at) WHERE published = FALSE;
```

The **Outbox pattern** ensures exactly-once event publishing across Postgres + Kafka.

## Sharding

For 1 B+ rows of bookings:
- Partition by `created_at` monthly.
- Shard by `show_id` (modulo) when single-instance Postgres becomes the bottleneck.
- Catalog stays single-instance — read-heavy, easy to replicate.

## Output

```
Catalog:    movies / theatres / screens / seats / shows
Booking:    holds / bookings / booking_seats / payments
Guard:      PK (show_id, seat_id) on booking_seats
Redis:      hold:{showId}:{seatId} (SETNX + TTL); layout cache
Outbox:     atomic event publishing
```
