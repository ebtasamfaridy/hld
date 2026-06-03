# 12 · Tic Tac Toe — Machine Coding Skeleton

```
src/main/java/com/tictactoe/
├── domain/
│   ├── Symbol.java
│   ├── Move.java
│   ├── BoardSnapshot.java
│   ├── Board.java                # O(1) win detection via incremental counters
│   ├── Player.java
│   ├── GameStatus.java
│   └── TurnOutcome.java          # sealed
├── strategy/
│   ├── PlayerInput.java
│   └── ScriptedInput.java        # for deterministic demo
├── bot/
│   ├── RandomBot.java
│   └── MinimaxBot.java
├── listener/
│   ├── GameListener.java
│   └── ConsoleLogger.java
├── Game.java                      # orchestrator + Builder
└── Main.java                      # demo
```

## Demo flow (Main)

1. 3×3 board, K=3.
2. Alice (X) uses ScriptedInput; Bob (O) uses MinimaxBot.
3. Run until win or draw.
4. Logger prints each move and the final outcome.
