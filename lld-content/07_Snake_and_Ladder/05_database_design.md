# 05 · Snake and Ladder — Database Design

V1 is in-memory; no DB. Mention this and pivot to **what a persistence layer would look like for a multi-tenant server (V2)**.

## V1: in-memory

```
Game lives in process memory.
Lost on restart.
Sufficient for: local play, hackathon, machine-coding round.
```

## V2: persisted multi-tenant server

Two sensible storage shapes:

### Option A — Document model (Mongo / DynamoDB)

```jsonc
// Collection: games
{
  "_id": "game-uuid",
  "status": "IN_PROGRESS",
  "board": {
    "size": 100,
    "snakes":  [ { "head": 99, "tail": 7 }, ... ],
    "ladders": [ { "bottom": 4, "top": 25 }, ... ]
  },
  "rule_pack": "TRADITIONAL_INDIA",
  "players": [
    { "id": "u1", "name": "Alice", "position": 32, "started": true },
    { "id": "u2", "name": "Bob",   "position": 5,  "started": true }
  ],
  "current_player_idx": 1,
  "history": [
    { "ts": "...", "playerId": "u1", "roll": 5, "from": 27, "to": 32 }
  ],
  "version": 17,
  "winner_id": null,
  "created_at": "...",
  "updated_at": "..."
}
```

A whole game is one document. Reads and writes are atomic at the document level. **Optimistic locking on `version`** prevents lost moves.

### Option B — Relational (Postgres) + audit log

```sql
CREATE TABLE games (
    id              UUID PRIMARY KEY,
    status          TEXT NOT NULL,
    board_json      JSONB NOT NULL,
    rule_pack       TEXT NOT NULL,
    current_player  INT  NOT NULL,
    winner_id       UUID NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE game_players (
    game_id   UUID,
    player_id UUID,
    name      TEXT,
    position  INT NOT NULL DEFAULT 0,
    started   BOOLEAN NOT NULL DEFAULT FALSE,
    seat      INT,                         -- turn order
    PRIMARY KEY (game_id, player_id)
);

CREATE TABLE game_moves (
    game_id     UUID,
    seq         BIGINT,
    player_id   UUID,
    roll_value  INT,
    from_cell   INT,
    to_cell     INT,
    jumper_kind TEXT NULL,                 -- SNAKE | LADDER | NULL
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, seq)
);

CREATE INDEX idx_game_moves_player ON game_moves (player_id, occurred_at DESC);
```

The relational shape gives:
- **Replay** of any game by selecting moves ordered by `seq`.
- **Stats** ("most snake bites this month") with simple SQL.
- **Multi-table joins** for friends-played-together.

### Choice

For a casual gaming product, **Option A (document)** wins on simplicity. For a stats-heavy / leaderboards product, **Option B (relational)** is worth the schema overhead.

## Concurrency on persisted games

Web client sends "I rolled, here's my move." Two clients (e.g., Alice on phone + iPad) submit in parallel.

- Server validates **only the current player** can submit.
- Server uses **optimistic version**: `UPDATE games SET ... WHERE id=? AND version=?`. Loser retries (or, since only one player should ever submit, errors out).

For the document model, MongoDB's `findOneAndUpdate` with `version` filter is the same pattern.

## Caching

A live game lives in memory in the active server. Persistence is the **fallback** for crash recovery. We don't cache from DB — DB writes happen *because* state has changed in memory.

## Output

```
V1:    in-memory only
V2:    one document per game (Mongo / Dynamo) — optimistic version,
       OR Postgres tables with games / game_players / game_moves
Lock:  optimistic on version; one writer per game (the active server)
```
