# 05 · Tic Tac Toe — Database Design

V1 is in-memory; the same caveats as `07_Snake_and_Ladder/05_database_design.md` apply.

## V2 — multi-tenant server

### Document model (recommended)

```jsonc
// Collection: games
{
  "_id": "game-uuid",
  "n": 3,
  "k": 3,
  "status": "IN_PROGRESS",
  "players": [
    { "id": "u1", "name": "Alice", "symbol": "X" },
    { "id": "u2", "name": "Bob",   "symbol": "O" }
  ],
  "current_player_idx": 1,
  "moves": [
    { "seq": 1, "playerId": "u1", "row": 1, "col": 1, "ts": "..." },
    { "seq": 2, "playerId": "u2", "row": 0, "col": 0, "ts": "..." }
  ],
  "winner_id": null,
  "version": 4,
  "created_at": "...",
  "updated_at": "..."
}
```

One document per game, atomic updates. Optimistic version on every move.

### Relational alternative

```sql
CREATE TABLE games (
    id UUID PRIMARY KEY,
    n INT NOT NULL,
    k INT NOT NULL,
    status TEXT NOT NULL,
    current_idx INT NOT NULL,
    winner_id UUID NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE game_players (
    game_id UUID,
    player_id UUID,
    name TEXT,
    symbol CHAR(1),
    seat INT,
    PRIMARY KEY (game_id, player_id)
);

CREATE TABLE game_moves (
    game_id UUID,
    seq INT,
    player_id UUID,
    row INT,
    col INT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, seq)
);
```

Use the document model unless leaderboards / aggregate queries are first-class. For a casual game, document wins.

## Replay

The `moves` array (or table) is sufficient to replay. Re-instantiate Game with the same player config; apply moves in sequence; assert state matches the persisted `winner_id`.

## Output

```
V1:    in-memory
V2:    one document per game; atomic updates with version CAS
Replay: derived from move log
```
