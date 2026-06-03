# 08 · Tic Tac Toe — Sequence Diagrams

## 1. Take a turn (valid move, game continues)

```mermaid
sequenceDiagram
    autonumber
    participant C as Caller
    participant G as Game
    participant P as Player
    participant I as PlayerInput
    participant B as Board
    participant L as Listeners

    C->>G: takeTurn()
    G->>B: snapshot()
    B-->>G: BoardSnapshot
    G->>P: input()
    P->>I: nextMove(snapshot, X)
    I-->>P: Move(1, 1)
    G->>G: validate(player, move)
    G->>B: place(1, 1, X)
    B-->>G: true
    G->>B: wonBy(X, 1, 1)
    B-->>G: false
    G->>B: isFull()
    B-->>G: false
    G->>G: currentIdx = (currentIdx+1) % N
    G->>L: onTurn(Moved)
    G-->>C: TurnOutcome.Moved
```

## 2. Winning move (3-in-a-row)

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant B as Board
    participant L as Listeners

    Note over B: rowCount[2][X.idx]=2 already
    G->>B: place(2, 2, X)
    G->>B: wonBy(X, 2, 2)
    Note over B: rowCount[2][X.idx]==3 == k → win
    B-->>G: true
    G->>L: onTurn(Won{Alice})
    Note over G: status = WON
```

## 3. Draw

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant B as Board
    participant L as Listeners

    G->>B: place(2, 2, O)
    G->>B: wonBy(O, 2, 2)
    B-->>G: false
    G->>B: isFull()
    B-->>G: true
    G->>L: onTurn(Drawn)
    Note over G: status = DRAWN
```

## 4. Invalid move (cell occupied)

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant P as Player
    participant I as PlayerInput

    G->>P: input()
    P->>I: nextMove(...)
    I-->>P: Move(0,0)
    Note over G: cells[0][0] != null
    G-->>G: TurnOutcome.Rejected{OCCUPIED}
```

V1: Rejected does not advance currentIdx — same player tries again. (V2 might count it as a forfeit on N retries; product call.)

## 5. Bot turn

```mermaid
sequenceDiagram
    autonumber
    participant G as Game
    participant Bot as MinimaxBot
    participant B as Board

    G->>Bot: nextMove(snapshot, O)
    loop minimax search (≤26K nodes for 3×3)
        Bot->>B: place / undo (on internal copy)
    end
    Bot-->>G: best move (e.g., (1,1))
    G->>B: place(1,1,O)
```

The bot operates on **its own Board copy** to avoid mutating the live game. Pass a `Board.copy()` not a snapshot if the bot needs `place/undo`.

## Output

```
takeTurn flow:   snapshot → input → validate → place → wonBy → isFull → emit
Win flow:        wonBy returns true on the move that completes K-in-a-row
Bot flow:        bot operates on its copy; returns final move
```
