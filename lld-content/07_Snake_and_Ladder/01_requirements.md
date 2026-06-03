# 01 · Snake and Ladder — Requirements

## Problem statement

Implement a multi-player Snake and Ladder game. Players take turns rolling a dice and move along a numbered board (typically 100 cells). Snakes pull a player down; ladders push them up. First to reach the last cell wins.

The interviewer's *real* probe: **can you separate game rules, board entities, and the loop**, and **can you accept new variations without breaking the model**?

---

## Functional requirements

### Core (in scope)

- Configurable **board size** (default 10×10 = 100 cells).
- Configurable **snakes** (head → tail, where head > tail).
- Configurable **ladders** (bottom → top, where bottom < top).
- 2 to N **players**.
- **Dice** with configurable side count (default: 1 die, 6 sides).
- Turn-based gameplay; round-robin order.
- A player who lands on a snake head moves to its tail; on a ladder bottom moves to its top.
- A player who lands beyond the last cell either:
  - **stays put** (default rule), or
  - is allowed to **win on overshoot** (variant rule),
  - per a configurable rule set.
- Must **roll the dice's max value** (typically 6) to start moving (variant rule).
- Game ends when one player reaches the last cell.
- Track and report **move history** for each player.
- Emit events (move, snake bite, ladder climb, win) to listeners.

### Extensions (acknowledged, deferred)

- Multiple games concurrently on a server (multiplayer service).
- Disconnected/timeout play (auto-skip turn after T seconds).
- Power-ups (skip-snake card, double-roll card).
- Multiple winners (top-3 finish); game continues after first winner.
- Animated replay (recompute from move history).
- Spectator mode.
- Multi-board joinable rooms (lobby system).

### Out of scope

- Networking / RPC (this is local LLD).
- Auth / matchmaking (server-side concern, separate problem).
- UI rendering (we expose state, the UI is whatever).

---

## Non-functional requirements

| NFR | Target | Why |
| --- | --- | --- |
| Determinism for tests | exact moves reproducible from a seed | Otherwise tests are flaky |
| Extensibility | adding a new "Jumper" type costs 1 class | OCP |
| Single-game memory | < 10 KB | Game state is small |
| Move latency | < 1 ms | Trivial |
| Thread safety | not required for V1 (single-threaded loop) | Game is sequential |
| Multi-game host | 100 K concurrent games | V2 server concern |

---

## Actors

```
Player           - has a name, a token, a position on the board
Game             - drives the loop, holds turn order, declares the winner
Board            - knows cells, snakes, ladders
Dice             - rolls integer values
GameRule         - validates "can this player move?", "did this player win?"
GameEventListener - notified of every event (UI, logger, replay)
```

---

## Edge cases (decisions)

| Case | Handling |
| --- | --- |
| Player rolls past last cell | Default: stay put. Configurable to win-on-overshoot. |
| Snake head AND ladder bottom on same cell | Validate at construction: forbidden. |
| Snake head = snake tail | Forbidden. |
| Ladder bottom = ladder top | Forbidden. |
| Cycles: snake tail is another snake's head | Forbidden (configurable to allow chain). |
| 6 must-start rule, player keeps rolling non-6 forever | Allowed; stuck at 0 until they roll 6. |
| Two players land on same cell | Allowed (no collision in classic rules). |
| Game with 1 player | Trivial; reaches end alone. (Refused at construction in V1.) |
| Player disconnects | V1: pre-recorded moves; no concept of disconnect. |
| Roll-again-on-6 rule | Configurable variant. |

---

## V1 vs V2

| Feature | V1 | V2 |
| --- | --- | --- |
| Single game session, in-memory | ✓ | |
| Configurable board / snakes / ladders | ✓ | |
| Pluggable dice strategy | ✓ | |
| Pluggable rule set | ✓ | |
| Event listeners | ✓ | |
| Multi-game server | | ✓ |
| Persistence / replay | | ✓ |
| Power-ups | | ✓ |
| Network play | | ✓ |

---

## Output

```
Actors:        Player, Game, Board, Dice, GameRule, GameEventListener
Core FR:       configurable board, multiple players, dice strategy,
               snakes & ladders as Jumpers, rule pack, event emission
NFR:           deterministic, OCP-extensible, fast, single-threaded V1
Out of scope:  network, auth, UI, persistence
Extensions:    multi-game server, disconnects, power-ups, replay
```
