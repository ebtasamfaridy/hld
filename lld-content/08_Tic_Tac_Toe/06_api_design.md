# 06 · Tic Tac Toe — API Design

## V1 — Programmatic

```java
Game game = new Game.Builder()
        .withSize(3, 3)               // n=3, k=3
        .addPlayer("Alice", new HumanInput(System.in))
        .addPlayer("Bob",   new MinimaxBot(/*depth*/9))
        .addListener(new ConsoleLogger())
        .build();

game.start();
while (game.status() == GameStatus.IN_PROGRESS) {
    game.takeTurn();
}
```

## V2 — REST + WebSocket

### REST

```http
POST /v1/games
{ "n": 3, "k": 3, "max_players": 2 }
→ 201 { "id": "...", "join_token": "...", "websocket_url": "..." }

POST /v1/games/{id}/join
→ 200 { "seat": 2, "symbol": "O", "websocket_url": "..." }

POST /v1/games/{id}/start
→ 200 OK
```

### WebSocket

```jsonc
// Client → Server
{ "type": "MOVE_REQUEST", "game_id": "...", "row": 1, "col": 1, "client_seq": 5 }

// Server → all clients
{ "type": "MOVE_APPLIED", "player_id": "u1", "row": 1, "col": 1, "seq": 3 }
{ "type": "MOVE_REJECTED", "player_id": "u1", "reason": "OCCUPIED" }
{ "type": "GAME_FINISHED", "winner_id": "u1" }
{ "type": "GAME_DRAWN" }
```

### Idempotency

`client_seq` per (game, player). Server caches last applied `client_seq` and short-circuits replays.

### Anti-cheat

Same as Snake & Ladder: server validates everything; clients are dumb terminals. For bots, the bot's "input" is server-generated — there's no network-side cheating because the client never tells the server what move the bot made.

## Pagination

Move history is at most N² entries; no pagination needed.

## Errors

```jsonc
HTTP/1.1 409 { "title": "Not your turn" }
HTTP/1.1 422 { "title": "Cell occupied", "row": 1, "col": 1 }
```

## Output

```
V1 API:  Game.start() / Game.takeTurn() / Game.status() / Game.winner()
V2 API:  REST for lifecycle, WebSocket for play
Anti-cheat: server-authoritative move validation
```
