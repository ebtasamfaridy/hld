# 13 · Tic Tac Toe — Extensions & Tradeoffs

## Extensions

### 1. N×N, K-in-a-row generalization
Already supported by Board(n, k). Same engine handles 5×5 K=4, 19×19 K=5 (Gomoku).

### 2. M players (≥3)
Player list can be any size; Symbol.idx is assigned per game. Win detection uses per-symbol counters — already general.

### 3. Misère
Opposite goal: forcing your opponent to form K-in-a-row makes you win. Add a flag on Game; on `wonBy=true`, the *current* player is the loser. One-line change.

### 4. Time controls
Per-move budget in `PlayerInput`. Bot wraps its `nextMove` with a `Duration` watchdog; on timeout, returns its best so far.

### 5. Replay from move log
Persist `List<Move>`. Re-instantiate Board; re-apply moves. Trivial.

### 6. AlphaZero-style bot
Replace `MinimaxBot` with `MCTSBot` — same `PlayerInput` interface. Game is unchanged.

### 7. Connect-Four variant (gravity)
Different game (gravity changes move semantics). Different aggregate; share Symbol/Player/Listener idioms.

### 8. Online matchmaking
Separate service; out of scope. The Game core is the same; its caller is a websocket actor.

## Tradeoffs

### Per-row counters vs bitboards

| Criterion | Per-row counters | Bitboards |
| --- | --- | --- |
| Code complexity | low | high |
| Win check | O(1) (k=n) or O(k) | O(1) via popcount on shifted masks |
| Memory | O(N × symbols) | O(symbols × words) |
| Bot training speed | good | best |
| Decision (V1) | **counters** ✓ |

Bitboards are a 2× speedup for bot training; not justified for V1.

### MinimaxBot for arbitrary N×N

| Criterion | Minimax | MCTS | Heuristic |
| --- | --- | --- | --- |
| 3×3 | trivial | overkill | works |
| 5×5 K=4 | ~10⁶ nodes | best | ok |
| 9×9 | 10¹² | best | required |
| Decision | minimax for V1 (3×3); MCTS for V2 (larger). |

### Snapshot vs Copy for bots

| Criterion | Snapshot (read-only) | Copy (mutable) |
| --- | --- | --- |
| Cost | O(N²) once | O(N²) once + O(1) per place/undo |
| Bot can search | no (no mutation) | yes |
| Decision | **Snapshot for default**; Copy when bot does search. |

### Mutable vs immutable Board

A persistent functional Board would let bots store positions in a transposition table by identity. Trade: every move allocates a new Board ⇒ GC pressure. We chose mutable + place/undo — the right call for in-process bot training.

## Open questions

- Should rejected moves count as forfeit after N retries? (UX call.)
- Multiple winners possible if 3 players? (Yes — first to K-in-a-row wins.)
- Time control format (chess clock vs per-move)? (Product call.)

## Output

```
Extensions:    N×N K-in-a-row, M players, misère, time controls, replay, AlphaZero
Pre-decided:   per-row counters (not bitboards), minimax for 3×3,
               snapshot+copy split, mutable Board with place/undo
Open Qs:       forfeit policy, time control format
```
