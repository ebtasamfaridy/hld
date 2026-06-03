# 10 · Snake and Ladder — Design Patterns

## 1. Strategy — `Dice`

**Problem.** Tests must be deterministic; production must be random; some experiments use weighted dice.

**Pattern.** `Dice` interface. Plug `StandardDice` in production, `FixedDice` in tests, `WeightedDice` in experiments.

**Tradeoff.** None worth mentioning. This is the canonical "swap implementation" case.

## 2. Strategy + Composite — `GameRule` + `RuleEngine`

**Problem.** Different rule packs (Indian traditional, casual, strict) share infrastructure (turns, board, dice) but differ on:
- must-roll-6-to-start,
- exact-finish vs win-on-overshoot,
- bonus-roll-on-6.

**Pattern.** Each rule is an independent `GameRule`. `RuleEngine` runs them in order, threading the "effective position" through them.

```java
for (GameRule r : rules) {
    var maybe = r.apply(p, roll, currentEffective, board);
    if (maybe.isEmpty()) return Optional.empty();
    currentEffective = maybe.get();
}
```

**Tradeoff.** Order matters. We document it (filtering rules first, transforming rules next). Otherwise rules could conflict.

## 3. Polymorphism — `Jumper` (Snake | Ladder)

**Problem.** Snake and Ladder share 100 % of the *mechanic* ("land here, go there") and only differ in *direction* and *theme*.

**Pattern.** A sealed `Jumper` interface; `Snake` and `Ladder` are records implementing it. Construction-time invariants enforce direction.

**Why sealed.** Compiler-checked exhaustive `switch` for listeners; no `instanceof` chains.

**Tradeoff.** If we add Teleporter later, it's a third permitted subtype — listeners get a compile-time warning to handle it.

## 4. Builder — `Game.Builder`

**Problem.** Game has many configuration knobs. Constructor with 7 args is unreadable.

**Pattern.** Fluent builder. Validates at `build()`.

**Tradeoff.** None worth mentioning at this scale.

## 5. Observer / Event Listener — `GameEventListener`

**Problem.** UI, console logger, replay recorder, scoreboard service all want notifications. Coupling them into Game = god class.

**Pattern.** `GameEventListener.onTurn(TurnOutcome)`. Game holds a list. Listeners subscribe at construction.

**Tradeoff.** Synchronous listeners in V1: a slow listener slows the loop. V2 we'd dispatch on a worker queue.

## 6. State pattern — implicit, via enum + transitions in Game

**Problem.** Game has 3 states; each method (`takeTurn`, `start`) is valid in some states only.

**Pattern.** Rather than `class WaitingGameState extends GameState` (overkill), we use an enum and explicit guards:

```java
public TurnOutcome takeTurn() {
    if (status != IN_PROGRESS) throw new IllegalStateException(...);
    ...
}
```

**Tradeoff.** Subclass-per-state is appropriate when *each* state has many distinct behaviors. Here, two methods × three states = small enough to inline.

## 7. Command (lite) — `TurnOutcome` as a record

`TurnOutcome` is a discriminated union of `Skipped | Moved | Won`. It is **passed to listeners as a record** — no abstract `Command.execute()` is needed because listeners consume, not execute.

(If we wanted *replay* via re-application, we'd promote it to true Command pattern with `apply(Game) → Game`. Captured as a V2 extension.)

## 8. Repository (only if persistent)

V1: not needed. V2: a `GameRepository` per `05_database_design.md`.

## What we deliberately avoid

| Pattern | Why not |
| --- | --- |
| **Singleton for Game** | Multiple games per server in V2 |
| **Inheritance hierarchy for Player types** | Bot vs Human vs RemotePlayer is a strategy, not subclass |
| **Visitor over Jumper** | sealed switch covers it more cleanly |
| **Decorator on Dice** | Theoretically nice, but YAGNI for V1 |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| Strategy | Dice | Swap RNG for tests / experiments |
| Strategy + Composite | GameRule + RuleEngine | Pluggable rule packs |
| Polymorphism (sealed) | Jumper | Unified abstraction for Snake/Ladder |
| Builder | Game.Builder | Readable construction |
| Observer | GameEventListener | Decouple notification |
| State (implicit) | Game.status | Validate operations against state |
| Discriminated union | TurnOutcome | Exhaustive listener handling |

## Output

A small set of patterns, each justified by a specific problem in the game. The interviewer's signal: do you reach for the right pattern at the right size, or do you over-engineer?
