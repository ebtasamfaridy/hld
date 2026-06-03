# 04 · Snake and Ladder — Domain Model

The whole game is six classes. Picking the right shapes is the entire interview.

## Aggregates and value objects

```text
Game (root)
├── Board
│   ├── int size
│   └── Map<int, Jumper> jumpers       # cell → snake/ladder
├── Dice                                # strategy
├── List<Player>
├── RuleEngine
├── GameStatus (WAITING | IN_PROGRESS | FINISHED)
├── int currentPlayerIdx
└── Optional<Player> winner

Board.Jumper                            # interface (sealed)
├── Snake (head, tail)                  # head > tail
└── Ladder (bottom, top)                # bottom < top

Player
├── String name
├── int position                        # 0 = off-board / start
└── List<MoveRecord> history

MoveRecord (rollValue, fromCell, intermediateCell, toCell, snakeOrLadder?)
```

## Why `position = 0` for off-board

The cell numbering is 1..N. Position 0 means "hasn't entered the board yet" — useful for the must-start-with-6 rule. Avoids treating "before cell 1" as a special-case state.

## `Jumper` interface

```java
public sealed interface Jumper permits Snake, Ladder {
    int from();
    int to();
    JumperKind kind();
}

public enum JumperKind { SNAKE, LADDER }

public record Snake(int head, int tail) implements Jumper {
    public Snake { if (head <= tail) throw new IllegalArgumentException("snake must descend"); }
    public int from() { return head; }
    public int to()   { return tail; }
    public JumperKind kind() { return JumperKind.SNAKE; }
}

public record Ladder(int bottom, int top) implements Jumper {
    public Ladder { if (bottom >= top) throw new IllegalArgumentException("ladder must ascend"); }
    public int from() { return bottom; }
    public int to()   { return top; }
    public JumperKind kind() { return JumperKind.LADDER; }
}
```

The discriminated union (`sealed`) means listeners can pattern-match without a runtime type check ladder.

## Board — invariants

```java
public final class Board {
    private final int size;
    private final Map<Integer, Jumper> jumpers;

    public Board(int size, List<Jumper> jumperList) {
        this.size = size;
        this.jumpers = new HashMap<>();
        for (Jumper j : jumperList) {
            require(j.from() >= 1 && j.from() <= size);
            require(j.to()   >= 1 && j.to()   <= size);
            require(!jumpers.containsKey(j.from()),
                    "two jumpers cannot share a 'from' cell");
            jumpers.put(j.from(), j);
        }
        // Optional: detect cycles (snake-tail-on-snake-head)
    }

    public Optional<Jumper> jumperAt(int cell) {
        return Optional.ofNullable(jumpers.get(cell));
    }

    public int size() { return size; }
}
```

The big checks happen at construction. After that the board is **immutable** — much easier to reason about.

## Dice — Strategy

```java
public interface Dice {
    int roll();           // 1..max for a single die
    int max();
}

public final class StandardDice implements Dice {
    private final int sides;
    private final RandomGenerator rng;
    public int roll() { return rng.nextInt(sides) + 1; }
    public int max()  { return sides; }
}

public final class FixedDice implements Dice {
    private final Iterator<Integer> rolls;
    public FixedDice(int... values) { rolls = Arrays.stream(values).iterator(); }
    public int roll() { return rolls.next(); }
    public int max()  { return 6; }
}
```

`FixedDice` is the test fixture. Without it your tests roll real dice — flaky tests guaranteed.

## RuleEngine — Composable validation

```java
public interface GameRule {
    /**
     * Returns the "effective" position after applying this rule, or
     * Optional.empty() if the move is forbidden (e.g., must roll 6 to start).
     */
    Optional<Integer> apply(Player p, int rollValue, int proposedPosition, Board b);
}

public final class MustRollMaxToStartRule implements GameRule { ... }
public final class StayOnOvershootRule    implements GameRule { ... }
public final class WinOnOvershootRule     implements GameRule { ... }
public final class ExactFinishRule        implements GameRule { ... }

public final class RuleEngine {
    private final List<GameRule> rules;
    public Optional<Integer> finalPosition(Player p, int roll, int proposed, Board b) {
        int cur = proposed;
        for (GameRule r : rules) {
            var next = r.apply(p, roll, cur, b);
            if (next.isEmpty()) return Optional.empty();    // forbidden
            cur = next.get();
        }
        return Optional.of(cur);
    }
}
```

Different rule packs give different games:
- `[MustRollMaxToStart, StayOnOvershoot]` = traditional Indian rules.
- `[WinOnOvershoot]` = casual rules.
- `[ExactFinish]` = strict.

Adding a new rule = new class. No existing class is touched. **OCP win.**

## Player

```java
public final class Player {
    private final String id;
    private final String name;
    private int position;
    private boolean started;
    private final List<MoveRecord> history = new ArrayList<>();

    public int position() { return position; }
    void moveTo(int newPosition) { this.position = newPosition; }
    void start() { this.started = true; }
    void recordMove(MoveRecord r) { history.add(r); }
}
```

Player is **package-private mutable** — only the Game (in the same package) can advance them. From outside, Player is read-only.

## Game — the orchestrator

```java
public final class Game {
    private final Board board;
    private final Dice dice;
    private final RuleEngine rules;
    private final List<Player> players;
    private final List<GameEventListener> listeners;
    private GameStatus status = GameStatus.WAITING;
    private int currentIdx = 0;
    private Player winner;

    public TurnOutcome takeTurn() {
        require(status == GameStatus.IN_PROGRESS);
        Player p = players.get(currentIdx);
        int roll = dice.roll();

        int proposed = p.position() + roll;
        Optional<Integer> finalCellOpt = rules.finalPosition(p, roll, proposed, board);

        TurnOutcome outcome;
        if (finalCellOpt.isEmpty()) {
            outcome = TurnOutcome.skipped(p, roll);
        } else {
            int finalCell = finalCellOpt.get();
            Optional<Jumper> jumper = board.jumperAt(finalCell);
            int landed = jumper.map(Jumper::to).orElse(finalCell);
            p.moveTo(landed);
            outcome = TurnOutcome.moved(p, roll, proposed, landed, jumper.orElse(null));

            if (landed == board.size()) {
                status = GameStatus.FINISHED;
                winner = p;
            }
        }
        listeners.forEach(l -> l.onTurn(outcome));
        currentIdx = (currentIdx + 1) % players.size();
        return outcome;
    }
}
```

The whole game core is ~30 lines. Everything that's not core is in the strategies.

## Domain events (for listeners)

```
- GameStarted        (game, players)
- DiceRolled         (player, value)
- PlayerSkipped      (player, reason)
- PlayerMoved        (player, from, to, jumper?)
- SnakeBite          (player, from, to)        # convenience event derived from PlayerMoved
- LadderClimbed      (player, from, to)
- GameFinished       (winner)
```

V1 emits a single `TurnOutcome`; the *listener* derives finer events. Or we can emit them directly — interviewer's call.

## Output

```
Aggregates:    Game, Board, Player
Value objects: Jumper (Snake|Ladder), MoveRecord, TurnOutcome
Strategies:    Dice, GameRule
Invariants:    Board immutable post-construction; Player mutated only by Game
```

The reason this domain works is that **almost everything is immutable**. The mutable bits are isolated to Game and Player; everything else is data.
