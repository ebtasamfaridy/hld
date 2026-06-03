# 07 · Snake and Ladder — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Enums & sealed types =====
    class GameStatus {
      <<enumeration>>
      WAITING
      IN_PROGRESS
      FINISHED
    }
    class JumperKind {
      <<enumeration>>
      SNAKE
      LADDER
    }
    class Jumper {
      <<sealed interface>>
      +from() int
      +to() int
      +kind() JumperKind
    }
    class TurnOutcome {
      <<sealed>>
    }
    class Moved {
      +Player player
      +int roll
      +int from
      +int to
      +Jumper jumper
    }
    class Skipped {
      +Player player
      +int roll
      +String reason
    }
    class Won {
      +Player player
      +int roll
    }
    TurnOutcome <|-- Moved
    TurnOutcome <|-- Skipped
    TurnOutcome <|-- Won

    %% ===== Domain =====
    class Snake {
      <<record>>
      -int from
      -int to
      +kind() JumperKind
    }
    class Ladder {
      <<record>>
      -int from
      -int to
      +kind() JumperKind
    }
    Jumper <|-- Snake
    Jumper <|-- Ladder

    class Board {
      -int size
      -Map~int,Jumper~ jumpers
      +jumperAt(cell) Optional~Jumper~
      +size() int
      +standard100() Builder$
    }
    class Builder {
      -int size
      -List~Jumper~ list
      +withSnake(head, tail) Builder
      +withLadder(bottom, top) Builder
      +build() Board
    }
    Board *-- Builder
    Board o-- "*" Jumper

    class Player {
      -String id
      -String name
      -int position
      -boolean started
      -List~MoveRecord~ history
      +id() String
      +name() String
      +position() int
      +started() boolean
      +history() List~MoveRecord~
    }
    class MoveRecord {
      <<record>>
      -Instant ts
      -int roll
      -int from
      -int proposed
      -int finalCell
      -Jumper jumper
      -boolean skipped
      -String reason
    }
    Player o-- "*" MoveRecord

    %% ===== Dice (Strategy) =====
    class Dice {
      <<interface>>
      +roll() int
      +max() int
    }
    class StandardDice {
      -int sides
      -Random rng
      +roll() int
      +max() int
    }
    class FixedDice {
      -int[] values
      -int idx
      +roll() int
      +max() int
    }
    Dice <|.. StandardDice
    Dice <|.. FixedDice

    %% ===== Rules (Strategy + Composite) =====
    class GameRule {
      <<interface>>
      +apply(player, roll, proposed, board) Optional~Integer~
      +denyReason() String
    }
    class ExactFinishRule {
      +apply(...) Optional~Integer~
    }
    class StayOnOvershootRule {
      +apply(...) Optional~Integer~
    }
    class MustRollMaxToStartRule {
      +apply(...) Optional~Integer~
    }
    GameRule <|.. ExactFinishRule
    GameRule <|.. StayOnOvershootRule
    GameRule <|.. MustRollMaxToStartRule

    class RuleEngine {
      -List~GameRule~ rules
      +finalPosition(player, roll, proposed, board) Outcome
    }
    class Outcome {
      <<record>>
      -boolean allowed
      -int finalCell
      -String denyReason
      +allowed(cell) Outcome$
      +denied(reason) Outcome$
    }
    RuleEngine *-- Outcome
    RuleEngine o-- "*" GameRule

    class RulePacks {
      <<utility>>
      +standard() List~GameRule~$
      +mustStartWithMax() List~GameRule~$
    }
    RulePacks ..> GameRule

    %% ===== Observer =====
    class GameEventListener {
      <<interface>>
      +onGameStarted()
      +onTurn(outcome)
      +onGameFinished(winner)
    }
    class ConsoleLogger {
      -PrintStream out
      +onGameStarted()
      +onTurn(outcome)
      +onGameFinished(winner)
    }
    GameEventListener <|.. ConsoleLogger

    %% ===== Game (orchestrator) =====
    class Game {
      -Board board
      -Dice dice
      -RuleEngine rules
      -List~Player~ players
      -List~GameEventListener~ listeners
      -Clock clock
      -GameStatus status
      -int currentIdx
      -Player winner
      +start()
      +takeTurn() TurnOutcome
      +status() GameStatus
      +winner() Optional~Player~
    }
    Game o-- "1" Board
    Game o-- "1" Dice
    Game o-- "1" RuleEngine
    Game o-- "*" Player
    Game o-- "*" GameEventListener
    Game ..> TurnOutcome
```

---

## Class diagram

```mermaid
classDiagram
    class Game {
      -Board board
      -Dice dice
      -RuleEngine rules
      -List~Player~ players
      -List~GameEventListener~ listeners
      -GameStatus status
      -int currentIdx
      -Player winner
      +start()
      +takeTurn() TurnOutcome
      +status() GameStatus
      +winner() Optional~Player~
    }

    class Board {
      -int size
      -Map~int,Jumper~ jumpers
      +jumperAt(cell) Optional~Jumper~
      +size() int
    }

    class Jumper {
      <<interface (sealed)>>
      +from() int
      +to() int
      +kind() JumperKind
    }
    class Snake {
      +head int
      +tail int
    }
    class Ladder {
      +bottom int
      +top int
    }
    Jumper <|-- Snake
    Jumper <|-- Ladder

    class Dice {
      <<interface>>
      +roll() int
      +max() int
    }
    class StandardDice
    class FixedDice
    Dice <|.. StandardDice
    Dice <|.. FixedDice

    class GameRule {
      <<interface>>
      +apply(player, roll, proposed, board) Optional~int~
    }
    class MustRollMaxToStartRule
    class StayOnOvershootRule
    class WinOnOvershootRule
    class ExactFinishRule
    GameRule <|.. MustRollMaxToStartRule
    GameRule <|.. StayOnOvershootRule
    GameRule <|.. WinOnOvershootRule
    GameRule <|.. ExactFinishRule

    class RuleEngine {
      -List~GameRule~ rules
      +finalPosition(player, roll, proposed, board) Optional~int~
    }

    class Player {
      -String id
      -String name
      -int position
      -boolean started
      -List~MoveRecord~ history
      +position() int
    }

    class GameEventListener {
      <<interface>>
      +onTurn(TurnOutcome)
    }
    class ConsoleLogger
    class ReplayRecorder
    GameEventListener <|.. ConsoleLogger
    GameEventListener <|.. ReplayRecorder

    Game o-- Board
    Game o-- Dice
    Game o-- RuleEngine
    Game o-- "1..N" Player
    Game o-- "*" GameEventListener
    Board o-- "*" Jumper
    RuleEngine o-- "*" GameRule
```

## Package layout

```
com.snakeladder
├── domain/
│   ├── Board.java
│   ├── Jumper.java          (sealed interface + Snake/Ladder records)
│   ├── Player.java
│   ├── MoveRecord.java
│   ├── TurnOutcome.java
│   ├── GameStatus.java
│   └── JumperKind.java
├── dice/
│   ├── Dice.java
│   ├── StandardDice.java
│   └── FixedDice.java
├── rule/
│   ├── GameRule.java
│   ├── RuleEngine.java
│   ├── MustRollMaxToStartRule.java
│   ├── StayOnOvershootRule.java
│   ├── WinOnOvershootRule.java
│   ├── ExactFinishRule.java
│   └── RulePacks.java       (preset combinations)
├── listener/
│   ├── GameEventListener.java
│   └── ConsoleLogger.java
├── Game.java
└── Main.java
```

## Why the Builder for `Game`?

Constructors with 7+ parameters are unreadable. A builder:

```java
new Game.Builder()
    .withBoard(...)
    .withDice(...)
    .addPlayer("Alice")
    .addPlayer("Bob")
    .build();
```

reads top-down and validates at `build()` time:
- ≥ 2 players
- non-null board, dice, rules
- all-unique player names

## Why `TurnOutcome` is a sealed interface

Three terminal cases:
- `Skipped` — player tried but rule blocked them.
- `Moved` — moved with optional jumper.
- `Won` — moved AND landed on last cell.

Listeners pattern-match for richer logging:

```java
switch (outcome) {
    case Skipped s -> log("skipped: " + s.reason());
    case Moved m   -> log(m.player().name() + " " + m.from() + "→" + m.to());
    case Won w     -> log("WINNER: " + w.player().name());
}
```

## Output

A small graph: `Game` aggregates Board / Dice / RuleEngine / Players / Listeners. Strategies (Dice, GameRule) are interfaces. The whole thing fits on one whiteboard.
