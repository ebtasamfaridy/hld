# 09 · Tic Tac Toe — State Machines

## Game

```mermaid
stateDiagram-v2
    [*] --> WAITING : Game.Builder.build()
    WAITING --> IN_PROGRESS : start()
    IN_PROGRESS --> IN_PROGRESS : takeTurn (Moved or Rejected)
    IN_PROGRESS --> WON  : takeTurn → wonBy=true
    IN_PROGRESS --> DRAWN : takeTurn → board full, no winner
    WON --> [*]
    DRAWN --> [*]
```

## TurnOutcome (per turn)

```mermaid
stateDiagram-v2
    [*] --> Moved    : valid move, no win, board not full
    [*] --> Won      : move completed K-in-a-row
    [*] --> Drawn    : move filled the last cell, no win
    [*] --> Rejected : illegal move
```

`Rejected` does **not** advance `currentIdx`. Same player must retry.

## Player turn ordering

```mermaid
stateDiagram-v2
    state "currentIdx = 0" as I0
    state "currentIdx = 1" as I1
    state "currentIdx = N-1" as IN

    I0 --> I1 : Moved
    I1 --> IN : Moved
    IN --> I0 : Moved (round-robin)
    I0 --> I0 : Rejected
    I1 --> I1 : Rejected
```

Round-robin advances on `Moved` only; on `Rejected`, the same player goes again.

## Output

```
Game:    WAITING → IN_PROGRESS → (WON | DRAWN)
Turn:    Moved | Won | Drawn | Rejected (sealed)
Order:   round-robin on Moved; stay on Rejected
```
