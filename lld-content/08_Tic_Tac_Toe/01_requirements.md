# 01 · Tic Tac Toe — Requirements

## Functional requirements

### Core
- 2 players, alternating turns.
- Default board: 3×3, K=3 (3 in a row to win).
- Configurable: N×N grid (3 ≤ N ≤ 19), K-in-a-row (3 ≤ K ≤ N), 2 to M players each with a unique symbol (default: 'X' and 'O').
- A move is `(player, row, col)`. Must be in bounds and target an empty cell.
- After every move, detect:
  - **Win**: K consecutive same-symbol cells in a row, column, or diagonal.
  - **Draw**: board full and no winner.
  - **Continue**: otherwise.
- Emit events for moves and game outcome.
- Support **bot** players: Random, Minimax (3×3), Heuristic (N×N where Minimax is too expensive).
- Support **undo** (one-step) for casual play.

### Out of scope
- Networking, persistence (touched lightly in V2 sections).
- UI (we expose state).
- Authentication / matchmaking.

### Extensions
- Multi-player (3+) on larger boards.
- Misère (loser is whoever forms K-in-a-row).
- Custom shapes (4-in-a-row "Connect Four"-style with gravity → out of scope, different game).
- Time controls per move.
- Replay from move log.

## Non-functional

| NFR | Target | Why |
| --- | --- | --- |
| Move latency | < 1 ms (Human), bot-dependent | Trivial |
| Win-detection | O(1) per move, not O(N²) | Bots iterate millions of positions |
| Memory | O(N²) | Inevitable |
| Determinism | bot RNG seedable | Tests |
| Thread-safety | not required for V1 | Sequential |

## Actors

```
Player        - has symbol; can be Human or Bot
Game          - drives the loop, validates moves, detects win/draw
Board         - holds cells, exposes O(1) incremental updates
Symbol        - X / O / etc. (extensible)
PlayerInput   - strategy: how does this player produce a move?
GameListener  - observer
```

## Edge cases

| Case | Handling |
| --- | --- |
| Move on occupied cell | reject with `INVALID_MOVE_OCCUPIED` |
| Move out of bounds | reject with `INVALID_MOVE_BOUNDS` |
| Move when game finished | reject with `GAME_ALREADY_OVER` |
| Move out of turn | reject with `NOT_YOUR_TURN` |
| K > N | reject at construction |
| Single player | reject at construction |
| Bot timeout | configurable; falls back to random |

## Output

```
Core:    N×N grid, K-in-a-row, M players, alternating turns,
         win/draw detection, move/event emission, bots
Out:     network, persistence, auth, UI
NFR:     O(1) win detection, sub-ms move
```
