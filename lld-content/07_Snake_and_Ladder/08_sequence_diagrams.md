# 08 · Snake and Ladder — Sequence Diagrams

## 1. Take a turn (happy path: regular move)

```mermaid
sequenceDiagram
    autonumber
    participant C as Caller
    participant G as Game
    participant D as Dice
    participant R as RuleEngine
    participant B as Board
    participant P as Player
    participant L as Listeners

    C->>G: takeTurn()
    G->>P: position()
    P-->>G: 27
    G->>D: roll()
    D-->>G: 5
    G->>R: finalPosition(p, 5, 32, board)
    R-->>G: Optional.of(32)
    G->>B: jumperAt(32)
    B-->>G: Optional.empty()
    G->>P: moveTo(32)
    G->>L: onTurn(Moved{27,32,jumper=null})
    G-->>C: TurnOutcome.Moved
```

## 2. Take a turn (lands on a snake)

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant D as Dice
    participant R as RuleEngine
    participant B as Board
    participant P as Player
    participant L as Listeners

    G->>D: roll()
    D-->>G: 4
    Note over G: position 95 + 4 = 99
    G->>R: finalPosition(p, 4, 99)
    R-->>G: Optional.of(99)
    G->>B: jumperAt(99)
    B-->>G: Snake{head=99, tail=7}
    G->>P: moveTo(7)
    G->>L: onTurn(Moved{95,7,jumper=Snake})
    Note over L: ConsoleLogger prints "🐍 SNAKE: 95→99→7"
```

## 3. Must-roll-6-to-start rule

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant D as Dice
    participant R as RuleEngine
    participant P as Player
    participant L as Listeners

    Note over P: position=0, started=false
    G->>D: roll()
    D-->>G: 4
    G->>R: finalPosition(p, 4, 4)
    Note over R: MustRollMaxToStartRule sees not-started + roll!=max
    R-->>G: Optional.empty()
    G->>L: onTurn(Skipped{reason=MUST_ROLL_MAX_TO_START})
    Note over P: position stays at 0
```

## 4. Win on landing exactly at last cell

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant D as Dice
    participant R as RuleEngine
    participant B as Board
    participant P as Player
    participant L as Listeners

    Note over P: position=98
    G->>D: roll()
    D-->>G: 2
    G->>R: finalPosition(p, 2, 100)
    R-->>G: Optional.of(100)
    G->>B: jumperAt(100)
    B-->>G: Optional.empty()
    G->>P: moveTo(100)
    Note over G: 100 == board.size() → status=FINISHED
    G->>L: onTurn(Won{p})
```

## 5. Overshoot — stay-put rule

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant D as Dice
    participant R as RuleEngine

    Note over G: player at 98, rolls 5 → proposed=103
    G->>R: finalPosition(p, 5, 103)
    Note over R: StayOnOvershootRule sees proposed > size
    R-->>G: Optional.of(98)   // pin to current
    Note over G: player stays at 98
```

The same call returns `Optional.of(103)` if `WinOnOvershootRule` is configured; the game then proceeds, applies any jumper at 100 (last cell), and detects win.

## 6. Full-game outline

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant L as Listeners

    G->>L: GameStarted
    loop until status == FINISHED
        G->>G: takeTurn()
        G->>L: onTurn(...)
    end
    G->>L: GameFinished(winner)
```

## Output

```
Each turn is: roll → ruleEngine → board lookup → mutate player → notify listeners.
At most one of {Moved, Skipped, Won}.
The Game is the only thing that mutates state.
```
