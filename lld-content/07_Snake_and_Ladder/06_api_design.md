# 06 · Snake and Ladder — API Design

For V1 (single-process LLD) the "API" is the public method surface of `Game`. For V2 (server) it's REST + WebSocket.

## V1 — Programmatic API

```java
// Construction — Builder pattern keeps the constructor sane
Game game = new Game.Builder()
        .withBoard(Board.standard100()
                .withSnake(99, 7)
                .withSnake(40, 3)
                .withLadder(4, 25)
                .withLadder(60, 80))
        .withDice(new StandardDice(6, new Random(42)))
        .withRules(RulePacks.TRADITIONAL_INDIA)
        .addPlayer("Alice")
        .addPlayer("Bob")
        .addListener(new ConsoleLogger())
        .build();

game.start();
while (game.status() == GameStatus.IN_PROGRESS) {
    game.takeTurn();
}
System.out.println("Winner: " + game.winner().name());
```

The public API surface:
```java
class Game {
    void start();
    TurnOutcome takeTurn();
    GameStatus status();
    Player currentPlayer();
    Optional<Player> winner();
    List<Player> players();        // unmodifiable view
}
```

Six methods. Anything beyond this is internal.

## V2 — HTTP/REST + WebSocket

### REST (game lifecycle)

```http
POST /v1/games
{
  "board_size": 100,
  "snakes": [{"head": 99, "tail": 7}, ...],
  "ladders": [{"bottom": 4, "top": 25}, ...],
  "rule_pack": "TRADITIONAL_INDIA",
  "max_players": 4
}
→ 201 Created
{
  "id": "game-uuid",
  "join_token": "...",
  "websocket_url": "wss://..."
}
```

```http
POST /v1/games/{id}/join          # bearer = user
→ 200 OK { "seat": 2, "websocket_url": "..." }

POST /v1/games/{id}/start          # only by host
→ 200 OK
```

### WebSocket protocol (live play)

```jsonc
// Client → Server
{ "type": "ROLL_REQUEST", "game_id": "...", "client_seq": 7 }

// Server → all clients in room
{ "type": "DICE_ROLLED",  "game_id": "...", "player_id": "u1", "value": 5 }
{ "type": "PLAYER_MOVED", "game_id": "...", "player_id": "u1",
  "from": 27, "to": 32, "jumper": null }
{ "type": "LADDER_CLIMBED", "player_id": "u1", "from": 32, "to": 47 }
{ "type": "TURN_CHANGED", "next_player_id": "u2" }
{ "type": "GAME_FINISHED", "winner_id": "u1" }
```

### Idempotency

Client sends `client_seq` (monotonic per client). Server tracks last seen `client_seq` per (game, player). Replay arrives → server replies with **last result** instead of double-rolling.

This mirrors the idempotency-key pattern from `00_End_To_End_LLD_Tutorial/07_api_design.md`.

### Authoritative dice rolls

The **server** rolls the dice. Clients never report their own roll value. Otherwise: trivial cheating.

Some implementations (e.g., turn-based, low-trust) let clients roll for UX, then server re-rolls authoritatively if the value seems too good — but standard is server-side dice.

### Reconnect

WebSocket drops → client reconnects with `last_event_seq` it received. Server replays events from the missed range. Game state is the durable source of truth.

## Pagination

Move history can be long for replays. Cursor-pagination by `seq`:

```
GET /v1/games/{id}/moves?after=42&limit=100
```

## Errors

```jsonc
HTTP/1.1 409 Conflict
{
  "type": "...",
  "title": "Not your turn",
  "status": 409,
  "current_player_id": "u2"
}
```

The "not your turn" error is the most common — clients must handle it gracefully (no double-tap submit).

## Output

```
V1 API:        Game.start() / Game.takeTurn() / Game.status() / Game.winner()
V2 API:        REST for lifecycle (create/join/start),
               WebSocket for play (DICE_ROLLED / PLAYER_MOVED / TURN_CHANGED / GAME_FINISHED)
Anti-cheat:    server rolls dice; client_seq for idempotency; reconnect via event seq
```
