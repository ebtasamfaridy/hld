# 12 · Snake and Ladder — Machine Coding Skeleton

In-memory Java skeleton for the single-game V1.

## Layout

```
src/main/java/com/snakeladder/
├── domain/
│   ├── Jumper.java           # sealed interface
│   ├── Snake.java
│   ├── Ladder.java
│   ├── JumperKind.java
│   ├── Board.java
│   ├── Player.java
│   ├── MoveRecord.java
│   ├── TurnOutcome.java      # sealed: Skipped | Moved | Won
│   └── GameStatus.java
├── dice/
│   ├── Dice.java
│   ├── StandardDice.java
│   └── FixedDice.java
├── rule/
│   ├── GameRule.java
│   ├── RuleEngine.java
│   ├── MustRollMaxToStartRule.java
│   ├── StayOnOvershootRule.java
│   ├── ExactFinishRule.java
│   └── RulePacks.java
├── listener/
│   ├── GameEventListener.java
│   └── ConsoleLogger.java
├── Game.java                 # orchestrator + Builder
└── Main.java
```

## Demo flow (Main)

1. Build a 100-cell board with 4 snakes + 4 ladders.
2. Use `FixedDice` so the run is deterministic.
3. 2 players: Alice, Bob.
4. Add `ConsoleLogger` listener.
5. Loop until `FINISHED`.
6. Print winner.
