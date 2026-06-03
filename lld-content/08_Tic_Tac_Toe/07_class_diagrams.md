# 07 · Tic Tac Toe — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`.

```mermaid
classDiagram
    %% ===== Enums & sealed types =====
    class GameStatus {
      <<enumeration>>
      WAITING
      IN_PROGRESS
      WON
      DRAWN
    }
    class Symbol {
      <<value type>>
      -String mark
      -int idx
      +mark() String
      +idx() int
      +of(mark, idx) Symbol$
    }
    class TurnOutcome {
      <<sealed interface>>
    }
    class Moved {
      <<record>>
      +Player player
      +Move move
    }
    class Won {
      <<record>>
      +Player player
      +Move move
    }
    class Drawn {
      <<record>>
      +Move lastMove
    }
    class Rejected {
      <<record>>
      +Player player
      +Move attempted
      +RejectReason reason
    }
    class RejectReason {
      <<enumeration>>
      OUT_OF_BOUNDS
      OCCUPIED
      NOT_YOUR_TURN
      GAME_OVER
    }
    TurnOutcome <|-- Moved
    TurnOutcome <|-- Won
    TurnOutcome <|-- Drawn
    TurnOutcome <|-- Rejected
    Rejected ..> RejectReason

    %% ===== Domain =====
    class Move {
      <<record>>
      -int row
      -int col
      -Symbol symbol
      +of(row, col, symbol) Move$
    }
    class BoardSnapshot {
      <<record>>
      -int n
      -int k
      -Symbol[][] cells
      -List~Move~ history
    }
    class Board {
      -int n
      -int k
      -int symbolCount
      -Symbol[][] cells
      -int[][] rowCount
      -int[][] colCount
      -int[] diagCount
      -int[] antiDiagCount
      -int filled
      -List~Move~ history
      +place(move) boolean
      +undo(r, c)
      +wonBy(symbol, r, c) boolean
      +isFull() boolean
      +cell(r, c) Symbol
      +n() int
      +snapshot() BoardSnapshot
    }
    Board ..> Move
    Board ..> BoardSnapshot
    Board o-- "n*n" Symbol

    class Player {
      -String id
      -String name
      -Symbol symbol
      -PlayerInput input
      +id() String
      +name() String
      +symbol() Symbol
      +input() PlayerInput
      +setInput(input)
    }
    Player o-- "1" Symbol
    Player o-- "1" PlayerInput

    %% ===== Strategy: PlayerInput =====
    class PlayerInput {
      <<interface>>
      +nextMove(snapshot, mySymbol) Move
    }
    class ScriptedInput {
      -Queue~Move~ queue
      +nextMove(snapshot, mySymbol) Move
    }
    class RandomBot {
      -Random rng
      +nextMove(snapshot, mySymbol) Move
    }
    class MinimaxBot {
      -List~Symbol~ allSymbols
      +nextMove(snapshot, mySymbol) Move
      -minimax(board, toMove, me, alpha, beta, depth) int
    }
    PlayerInput <|.. ScriptedInput
    PlayerInput <|.. RandomBot
    PlayerInput <|.. MinimaxBot

    %% ===== Observer =====
    class GameListener {
      <<interface>>
      +onStart()
      +onTurn(outcome)
      +onFinish(winner)
    }
    class ConsoleLogger {
      -PrintStream out
      -Board boardRef
      +onStart()
      +onTurn(outcome)
      +onFinish(winner)
    }
    GameListener <|.. ConsoleLogger

    %% ===== Game (orchestrator) =====
    class Game {
      -Board board
      -List~Player~ players
      -List~GameListener~ listeners
      -GameStatus status
      -int currentIdx
      -Player winner
      +start()
      +takeTurn() TurnOutcome
      +status() GameStatus
      +winner() Optional~Player~
    }
    Game o-- "1" Board
    Game o-- "*" Player
    Game o-- "*" GameListener
    Game ..> TurnOutcome
```

---



## Class diagram

```mermaid
classDiagram
    class Game {
      -Board board
      -List~Player~ players
      -List~GameListener~ listeners
      -GameStatus status
      -int currentIdx
      -Player winner
      +start()
      +takeTurn() TurnOutcome
      +status()
      +winner() Optional~Player~
    }

    class Board {
      -int n
      -int k
      -Symbol[][] cells
      -int[][] rowCount
      -int[][] colCount
      -int[] diagCount
      -int[] antiDiagCount
      -int filled
      +place(r,c,s) boolean
      +undo(r,c,s) boolean
      +wonBy(s,r,c) boolean
      +isFull() boolean
      +snapshot() BoardSnapshot
    }

    class Player {
      -String id
      -String name
      -Symbol symbol
      -PlayerInput input
    }

    class PlayerInput {
      <<interface>>
      +nextMove(snapshot, mySymbol) Move
    }
    class HumanInput
    class RandomBot
    class MinimaxBot
    class HeuristicBot
    PlayerInput <|.. HumanInput
    PlayerInput <|.. RandomBot
    PlayerInput <|.. MinimaxBot
    PlayerInput <|.. HeuristicBot

    class TurnOutcome {
      <<sealed>>
    }
    class Moved
    class Won
    class Drawn
    class Rejected
    TurnOutcome <|-- Moved
    TurnOutcome <|-- Won
    TurnOutcome <|-- Drawn
    TurnOutcome <|-- Rejected

    class GameListener {
      <<interface>>
      +onTurn(TurnOutcome)
    }

    Game o-- Board
    Game o-- "M" Player
    Game o-- "*" GameListener
    Player o-- PlayerInput
```

## Package layout

```
com.tictactoe
├── domain/
│   ├── Symbol.java
│   ├── Move.java
│   ├── Board.java
│   ├── BoardSnapshot.java
│   ├── Player.java
│   ├── GameStatus.java
│   └── TurnOutcome.java
├── strategy/
│   ├── PlayerInput.java
│   └── HumanInput.java
├── bot/
│   ├── RandomBot.java
│   ├── MinimaxBot.java
│   └── HeuristicBot.java
├── listener/
│   ├── GameListener.java
│   └── ConsoleLogger.java
├── Game.java
└── Main.java
```

## Why `Player.input` and not subclasses

Suppose we modeled `class HumanPlayer extends Player`, `class BotPlayer extends Player`. Now adding "Player with optional time-control" means subclassing each. **Composition over inheritance**: Player has-a PlayerInput.

Switching a player from human to bot mid-game is a setter on `Player.input` — useful for "AFK auto-bot" mode.

## MinimaxBot — quick algorithm note

For 3×3 with K=3, the full game tree has ~26 K positions. Full minimax (no memoization) terminates in milliseconds.

```java
int minimax(Board b, Symbol toMove, Symbol me) {
    if (b.wonBy(me, lastR, lastC))    return +10;
    if (b.wonBy(opp(me), lastR, lastC)) return -10;
    if (b.isFull())                  return 0;
    int best = (toMove == me) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    for (each empty cell (r, c)) {
        b.place(r, c, toMove);
        int v = minimax(b, opp(toMove), me);
        b.undo(r, c, toMove);
        best = (toMove == me) ? max(best, v) : min(best, v);
    }
    return best;
}
```

Alpha-beta pruning + transposition table makes it fast at N=4. At N=5+ K=4, switch to heuristic / MCTS.

## Output

A graph of 5 main classes. Players are composed of PlayerInput (Strategy). TurnOutcome is sealed for exhaustive listener handling.
