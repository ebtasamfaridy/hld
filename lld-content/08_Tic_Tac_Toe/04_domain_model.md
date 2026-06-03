# 04 · Tic Tac Toe — Domain Model

## Aggregates and value objects

```text
Game (root)
├── Board
│   ├── int n
│   ├── int k
│   ├── Symbol[][] cells
│   ├── int[][] rowCount       # rowCount[row][symbolIdx]
│   ├── int[][] colCount
│   ├── int[]   diagCount
│   ├── int[]   antiDiagCount
│   ├── int     filled
│   └── List<Move> history
├── List<Player>
├── int currentIdx
├── GameStatus
└── Optional<Player> winner

Player
├── String id
├── String name
├── Symbol symbol
└── PlayerInput input

Move (record): row, col, symbol, ts

Symbol (record): char glyph, int idx       # idx is for counter arrays

GameStatus enum: WAITING | IN_PROGRESS | WON | DRAWN

TurnOutcome (sealed): Moved | Won | Drawn | Rejected
```

## Symbol as a value object (not enum)

Why not `enum Symbol { X, O }`? Because we want **N players** with custom symbols (`A, B, C, D` on a 5-player board). A `record Symbol(char glyph, int idx)` lets each game define its own symbol set.

`idx` is the column index in the counter arrays — assigned at game construction.

## Board

```java
public final class Board {
    private final int n, k;
    private final int symbolCount;
    private final Symbol[][] cells;
    private final int[][] rowCount;        // [row][symbolIdx]
    private final int[][] colCount;
    private final int[]   diagCount;       // [symbolIdx]
    private final int[]   antiDiagCount;
    private int filled = 0;

    public boolean place(int r, int c, Symbol s) {
        if (cells[r][c] != null) return false;
        cells[r][c] = s;
        rowCount[r][s.idx()]++;
        colCount[c][s.idx()]++;
        if (r == c)         diagCount[s.idx()]++;
        if (r + c == n - 1) antiDiagCount[s.idx()]++;
        filled++;
        return true;
    }

    public boolean undo(int r, int c, Symbol s) {     // mirror of place
        cells[r][c] = null;
        rowCount[r][s.idx()]--;
        colCount[c][s.idx()]--;
        if (r == c)         diagCount[s.idx()]--;
        if (r + c == n - 1) antiDiagCount[s.idx()]--;
        filled--;
        return true;
    }

    public boolean wonBy(Symbol s, int r, int c) {
        if (k == n) {
            return rowCount[r][s.idx()] == n
                || colCount[c][s.idx()] == n
                || (r == c && diagCount[s.idx()] == n)
                || (r + c == n - 1 && antiDiagCount[s.idx()] == n);
        }
        // k < n: scan windows of size k along the 4 lines through (r, c)
        return scanLine(s, r, c, 0, 1)        // row
            || scanLine(s, r, c, 1, 0)        // col
            || scanLine(s, r, c, 1, 1)        // diag
            || scanLine(s, r, c, 1, -1);      // anti-diag
    }

    public boolean isFull() { return filled == n * n; }
}
```

- `place` is O(1).
- `wonBy` is O(1) for the K=N case (most common: 3-in-a-row on 3×3).
- `wonBy` is O(K) for K<N (Gomoku-style). Still constant in N.

The interview-grade insight: **most candidates write O(N²) win detection.** State the right algorithm.

## Move

```java
public record Move(int row, int col, Symbol symbol, Instant at) {
    public Move {
        if (row < 0 || col < 0) throw new IllegalArgumentException("non-negative");
    }
}
```

## TurnOutcome

```java
public sealed interface TurnOutcome
        permits TurnOutcome.Moved, TurnOutcome.Won, TurnOutcome.Drawn, TurnOutcome.Rejected {

    record Moved(Player player, Move move) implements TurnOutcome {}
    record Won(Player player, Move move) implements TurnOutcome {}
    record Drawn(Move lastMove) implements TurnOutcome {}
    record Rejected(Player player, Move attempted, RejectReason reason) implements TurnOutcome {}

    enum RejectReason { OUT_OF_BOUNDS, OCCUPIED, NOT_YOUR_TURN, GAME_OVER }
}
```

## Player + PlayerInput

```java
public final class Player {
    private final String id;
    private final String name;
    private final Symbol symbol;
    private final PlayerInput input;
}

public interface PlayerInput {
    Move nextMove(BoardSnapshot snapshot, Symbol mySymbol);
}

public record BoardSnapshot(int n, int k, Symbol[][] cells, List<Move> history) {}
```

`BoardSnapshot` is a frozen view passed to bots. Mutating it (or the original board through it) is undefined behavior — we can `Arrays.copyOf` defensively if performance allows.

## Game

```java
public TurnOutcome takeTurn() {
    require(status == IN_PROGRESS);
    Player p = players.get(currentIdx);
    Move m = p.input().nextMove(board.snapshot(), p.symbol());

    var rejection = validate(p, m);
    if (rejection.isPresent()) {
        return new TurnOutcome.Rejected(p, m, rejection.get());
    }

    board.place(m.row(), m.col(), p.symbol());
    if (board.wonBy(p.symbol(), m.row(), m.col())) {
        status = WON;
        winner = p;
        emit(new TurnOutcome.Won(p, m));
        return ...;
    }
    if (board.isFull()) {
        status = DRAWN;
        emit(new TurnOutcome.Drawn(m));
        return ...;
    }
    currentIdx = (currentIdx + 1) % players.size();
    emit(new TurnOutcome.Moved(p, m));
    return ...;
}
```

## Output

```
Aggregates:    Game, Board, Player
Value objects: Move, Symbol, TurnOutcome (sealed)
Strategies:    PlayerInput
Trick:         O(1) win detection via incremental counters
```
