# 09 · Snake and Ladder — State Machines

## Game state machine

```mermaid
stateDiagram-v2
    [*] --> WAITING : new Game(...)
    WAITING --> IN_PROGRESS : start()
    IN_PROGRESS --> IN_PROGRESS : takeTurn() (no winner)
    IN_PROGRESS --> FINISHED : takeTurn() lands on cell N
    FINISHED --> [*]
    WAITING --> [*] : abort()      (V2)
    IN_PROGRESS --> ABANDONED : timeout / disconnect (V2)
```

V1 has three states: `WAITING`, `IN_PROGRESS`, `FINISHED`.

V2 adds `ABANDONED` for server-hosted games when all players disconnect.

## Player state machine

```mermaid
stateDiagram-v2
    [*] --> NOT_STARTED : created
    NOT_STARTED --> ON_BOARD : first valid roll
    ON_BOARD --> ON_BOARD : valid roll, not winning
    ON_BOARD --> WON : lands on cell N
    NOT_STARTED --> NOT_STARTED : invalid start roll
    WON --> [*]
```

`NOT_STARTED` matters only when `MustRollMaxToStartRule` is in effect — otherwise players are `ON_BOARD` from move 1.

## TurnOutcome — discriminated union

Not strictly a state machine, but the same idea: every turn produces *exactly one* of:

```mermaid
stateDiagram-v2
    [*] --> Skipped : rule denied move
    [*] --> Moved   : valid move, no winner
    [*] --> Won     : moved AND landed on N
```

Listeners pattern-match on the variant. Each variant carries its own data:
- `Skipped(player, roll, reason)`
- `Moved(player, from, to, jumper?)`
- `Won(player)`

## Why no "rolling dice" intermediate state?

In a turn-based game, the dice roll is **synchronous**. There's no observable "rolling" state at the domain level. (For an animated UI, the UI can show a roll animation; that's UI concern, not domain.)

If we ever made dice asynchronous (e.g., third-party fairness oracle), we'd add `WAITING_DICE` → `DICE_RESOLVED` to the turn substate machine — but that's V2.

## Output

```
Game:    WAITING → IN_PROGRESS → FINISHED  (V2: + ABANDONED)
Player:  NOT_STARTED → ON_BOARD → WON
Turn:    one of {Skipped, Moved, Won}
```

The state surface is small and explicit. That's the whole point — for a game this simple, the *clarity* of the model is what's being tested, not its complexity.
