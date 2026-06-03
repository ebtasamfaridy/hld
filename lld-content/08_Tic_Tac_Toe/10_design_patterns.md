# 10 · Tic Tac Toe — Design Patterns

## 1. Strategy — `PlayerInput`
**Problem.** A player can be human keyboard, bot, scripted replay, or remote websocket. Same Game logic.
**Pattern.** `PlayerInput.nextMove(snapshot, mySymbol) → Move`. Plug different implementations.

## 2. Composition — `Player has-a PlayerInput`
**Problem.** Avoid `class HumanPlayer extends Player` / `class BotPlayer extends Player` explosion.
**Pattern.** Player owns a PlayerInput. Swappable mid-game (AFK auto-bot).

## 3. Builder — `Game.Builder`
Fluent, validated construction.

## 4. Observer — `GameListener`
UI / logger / replay recorder subscribe.

## 5. Command + Memento — `Move` + `Board.undo`
**Problem.** Bots need to explore moves; undo is a first-class operation.
**Pattern.** `Move` is a record (Command). `Board.place(...)` and `Board.undo(...)` are mirror operations. Together they let any caller try-and-revert without copying the whole board.

The Minimax bot uses this **inside** its search — orders of magnitude cheaper than `board.copy()`.

## 6. Discriminated union — `TurnOutcome`
Sealed interface; listeners pattern-match.

## 7. Snapshot pattern — `BoardSnapshot`
**Problem.** A bot needs read-only access to the board. Mutating the live board would be a bug.
**Pattern.** Board exposes `snapshot()` — a frozen view (immutable record with copies). Bots that *need* mutation (Minimax search) get a `Board.copy()` instead, which is full-fidelity.

## 8. Object Pool (V2 perf)
**Problem.** Bot self-play creates millions of Board copies → GC pressure.
**Pattern.** Reusable Board pool: bots check out a Board, mutate, check it back in.

V1 doesn't need this; mention as scaling lever.

## 9. State pattern (implicit)
GameStatus enum + guards in Game.takeTurn. Same rationale as Snake & Ladder: 3 states with simple guards = enum is fine.

## What we avoid

| Pattern | Why not |
| --- | --- |
| Subclass-per-PlayerType | composition wins |
| Visitor over TurnOutcome | sealed switch is cleaner |
| Singleton Game | multi-game support |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | PlayerInput | Plug human / bot / replay |
| Composition | Player → Input | Avoid subclass explosion |
| Builder | Game.Builder | Readable construction |
| Observer | GameListener | Decouple notification |
| Command + Memento | Move + Board.undo | Bot search efficiency |
| Discriminated union | TurnOutcome | Exhaustive listener handling |
| Snapshot | BoardSnapshot | Read-only bot view |
| Object Pool | (V2) | Reduce GC in bot training |

## Output

The big idea: the engine's complexity is in **performance** (O(1) win detection, undo for bots), not in object orientation. The patterns we pick exist to support performance and pluggability.
