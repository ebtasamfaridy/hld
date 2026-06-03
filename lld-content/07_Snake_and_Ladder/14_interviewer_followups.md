# 14 · Snake and Ladder — Interviewer Follow-ups

## Q1. "Why are Snake and Ladder one abstraction, not two classes?"

Because they share 100 % of the *mechanic*: "if you land here, you go there." The difference is direction (`head > tail` vs `bottom < top`) and theme. Modeling them separately doubles the code and prevents adding Teleporter / Trapdoor without duplicating the same wiring again.

If they push: "What if a Snake had a 'venom' attribute and a Ladder had a 'rung count'?" → Then they're different things; we'd add subtype-specific fields. But for the standard game, they're not.

---

## Q2. "How do you make this testable?"

- **`Dice` strategy** with a `FixedDice` test fixture. Tests pass an exact sequence of rolls and assert exact final positions.
- **Pure Game state** — players are mutated only by Game; listeners can't mutate.
- **Deterministic Clock injection** — for any time-based extensions.
- **Rule classes are independent**: each can be unit-tested in isolation by giving it a Player + Board + roll.

---

## Q3. "Walk me through what happens on a specific roll."

(Use sequence diagrams from `08_sequence_diagrams.md`.) Game asks Dice → applies RuleEngine → checks Board for jumper → mutates Player → emits TurnOutcome. ~30 LOC end-to-end.

---

## Q4. "Two players land on the same cell. Anything happen?"

In the standard rules, no. Cells are not "occupied." We could add a `KillRule` (occupant kicked back to start) — but that's a `GameRule` you plug in.

---

## Q5. "How do I change the dice to roll twice and sum?"

`DoubleRollDice implements Dice { int roll() { return inner.roll() + inner.roll(); } }`. Decorator pattern. No engine change.

---

## Q6. "What about multi-die summing 3 dice?"

`MultiDice(int n, Dice base) { roll = sum of n base.roll() }`. Same Decorator.

---

## Q7. "How would you add a 'bonus roll on 6' rule?"

Two options:
1. **Modify RuleEngine.run** to loop while last roll == max — invasive.
2. **A `BonusRoll` wrapper on Game.takeTurn** — outer logic. Better: it's a turn-level concern, not a position-rule concern.

Pragmatic answer: add a `bonusRollOnMax: bool` flag on Game; in `takeTurn`, if roll == dice.max() and the move was successful, do not advance `currentIdx`. Keep it simple.

---

## Q8. "What if the game has 6 players and one disconnects?"

V1: not modeled. V2: each player has a `state` (`ACTIVE | DISCONNECTED | LEFT`); Game skips disconnected players with a timeout. After M missed turns, mark as `LEFT`. Game ends when ≤1 active.

---

## Q9. "How do you persist game state for crash recovery?"

Document model: serialize the whole `Game` to JSON after each turn (mainly Board config + Player positions + currentIdx + history). On restart, deserialize. Optimistic version on the document for race-free updates. See `05_database_design.md`.

---

## Q10. "Suppose one game on the server has a million spectators. Performance?"

Don't broadcast every move to every spectator. Batch into snapshots every 200ms. Or use a fan-out service: the game emits to a single Kafka topic; a fan-out service reads and pushes WebSocket frames at controlled rate.

Engine itself isn't bottlenecked — moves are O(1).

---

## Q11. "What if we want a 100M-cell board?"

Pure data: `Map<int, Jumper>` only stores cells *with* jumpers. Player position is just an int. Memory unaffected by board size (only by jumper count). Engine still O(1) per move.

UI is the constraint, not the engine.

---

## Q12. "Show me the smallest set of changes to support 'Race Mode' (first to N cells, no snakes)."

1. Pass `Board` with no jumpers (already supported — empty `List<Jumper>`).
2. Choose a rule pack that doesn't require max-roll to start (`RulePacks.casual`).
3. Done.

Demonstrates how the engine generalizes.

---

## Q13. "Why does Game.takeTurn return TurnOutcome instead of void?"

Two reasons:
1. **Caller-driven loop** — caller can decide whether to print, persist, or retry. Caller gets full control.
2. **Testability** — assert the exact return without subscribing to listeners.

Listeners are for fan-out; the return is for the orchestrator.

---

## Q14. "Where would `KillRule` (kick on collision) fit?"

It's *not* a `GameRule` (which only computes the player's own position). It's a post-move side effect: "after this player moves, mutate other players who share the destination cell." This belongs in a `PostMoveHook` interface — different from `GameRule`. Add it as V2.

The signal: **don't force features into the wrong abstraction.**

---

## Q15. "Why use `sealed` Jumper instead of just `interface`?"

- The compiler enforces exhaustive `switch` for listeners.
- New jumper types are intentionally added (you must update the `permits` clause).
- It's pure documentation: anyone reading `Jumper.java` sees all subtypes.

Trade: external libs can't add a Jumper. For an *internal* game, that's a feature, not a bug.

---

## Q16. "Most-likely bug a candidate writes here?"

Two:
1. **Snake/Ladder check happens *before* applying the rule pack** — misses the win-on-overshoot interaction.
2. **`currentIdx` advance happens before win check** — game declares wrong winner.

I'd review `Game.takeTurn` line by line if it doesn't pass my standard test cases.

---

## Output

```
Drill questions covered:
- Why one Jumper class
- Testability story (FixedDice + clock injection)
- Bonus rolls / power-ups
- Disconnects (V2)
- Persistence (V2)
- Performance under spectator load
- Generalization (race mode, huge board)
- Design pitfalls (KillRule placement, currentIdx ordering)
```
