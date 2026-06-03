# 14 · Tic Tac Toe — Interviewer Follow-ups

## Q1. "How do you check for win without scanning the board?"

Per-row, per-column, per-diagonal counters keyed by symbol. Increment on `place`, decrement on `undo`. After a move at (r, c) by `s`, check if any of the four counters for `s` equals `k`. O(1) for k=n; O(k) for k<n via window scan.

If they push: explain the bitboard alternative (1-bit per cell per symbol; popcount on shifted masks). Note that bitboards are 2-3× faster but not necessary at this scale.

---

## Q2. "Generalize to N×N."

Already done — `Board(n, k, symbolCount)`. Counter arrays size with N. Win check stays O(1) for k=n, O(k) for k<n.

---

## Q3. "Three players. Show me the code change."

Build with three `addPlayer(...)` calls. Each gets a unique Symbol with a unique idx. Board's `symbolCount` is now 3, counter arrays have an extra column. Game's round-robin already handles N players. No engine code changes.

---

## Q4. "Bot must move within 100ms."

Wrap `MinimaxBot` with a `TimeBudgetBot` decorator: spawn a thread/future for `nextMove`, race against `Duration`. If timeout, fall back to last-best-move tracked during search (iterative deepening). For 3×3 this never triggers; for 5×5 it does.

---

## Q5. "What if the human types invalid input?"

`HumanInput` validates parsing locally and re-prompts. The Game's `validate(...)` is the second line of defense. Defense in depth.

---

## Q6. "Why is `Symbol` not just a `char`?"

Because we need `idx` for the counter arrays. A `record` carries both `glyph` (display) and `idx` (storage offset). Plain `char` would force a separate map char→idx, which is awkward.

---

## Q7. "How would you store games for replay?"

Document model with the move log (`05_database_design.md`). Replay = re-construct Board, apply moves in `seq` order, assert final state matches stored `winner_id`.

---

## Q8. "Concurrency? Two players submit at once?"

Server-authoritative turn logic: only the current player's submission is applied; others are 409'd. WebSocket idempotency on `client_seq` to absorb retries. See `11_concurrency_and_scaling.md`.

---

## Q9. "Bot crashes mid-search."

`PlayerInput.nextMove` should never throw. We wrap in try/catch in Game; on exception, count it as a forfeit (reject reason `BOT_ERROR`). The opposing player wins by default.

---

## Q10. "Most candidates write O(N²) win-detection. Show me how you'd review their code."

I'd walk through the operations:
- "Where does `place` update state?"
- "Where does `wonBy` look up state?"
- "How long does each take?"

If `wonBy` walks all of `cells[][]`, that's the bug. Then I'd ask "What invariant could you maintain to make this O(1)?" — guiding them to per-line counters.

---

## Q11. "What's the smallest change to support a 'last-move highlights' UI feature?"

`Game.lastMove()` getter or include `lastMove` in `BoardSnapshot`. Listeners already receive the move on `Moved/Won/Drawn` outcomes — UI consumes those.

---

## Q12. "How do you test the win-detection?"

Property-based test: generate random sequences of valid moves; cross-check the O(1) win-detection against an independent O(N²) reference implementation. Mismatch = bug. Run for thousands of seeds.

---

## Q13. "What about 'two-in-a-row gives bonus' as a rule?"

That's a different game ("Connect 6"). Different aggregate; reuse Symbol/Player/Listener; new Board. Don't bend Tic Tac Toe to absorb it.

---

## Q14. "Why use `sealed TurnOutcome` instead of returning `null`?"

- Pattern-matching exhaustiveness — Java compiler checks all cases.
- Self-documenting — readers see all outcomes in one place.
- No `null` checks scattered.

---

## Q15. "What was the deepest mistake you avoided?"

Not modeling Symbol as a value object with `idx`. Without it, win-detection counters need a `Map<Char, int[]>` — slow, error-prone. With `Symbol.idx`, it's an `int[][]`. Speed matters for bot training.

---

## Output

```
Drill questions covered:
- O(1) win detection technique
- N×N, K-in-a-row, M-player generalization
- Bot time budget / failure handling
- Snapshot vs Copy for bots
- Server-authoritative validation
- Property-based testing of win-detection
- Why Symbol is a record with idx
```
