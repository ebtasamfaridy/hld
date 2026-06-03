# 13 · Snake and Ladder — Extensions & Tradeoffs

## Extensions

### 1. Multi-game server (V2)
Sticky-routed actor per game; persistence per move. See `11_concurrency_and_scaling.md`.

### 2. Power-ups
Add `Powerup` strategy that mutates `TurnOutcome` post-move. Examples: `SkipNextSnake`, `DoubleRoll`. Each is a class implementing `Powerup.applyAfter(TurnOutcome) → TurnOutcome`.

### 3. Bot players
Bots are just players with an automatic input source. Define `PlayerInputSource` (human via UI, bot via timer). Game polls the source instead of blocking.

### 4. Top-3 finish
Game continues after first winner; track ranking in `List<Player> finishers`. End condition becomes `finishers.size() == players.size() - 1` (last player not declared).

### 5. Replay
Persist `MoveRecord` list. Replay = re-instantiate Game with `FixedDice` from recorded rolls. Same domain, no extra logic.

### 6. Multi-die / weighted dice
Already supported via `Dice` strategy. Add `MultiDice(int n, Dice each) { roll() = sum }` decorator.

### 7. Custom Jumper types
`Teleporter` (random destination), `Trapdoor` (one-way), `Spring` (forward N cells regardless of current). Each is a new `permits` entry on the sealed `Jumper`.

### 8. Animated client
Server emits per-cell tweens; client interpolates. Domain unchanged.

### 9. Difficulty levels
Pre-canned snake/ladder layouts: `BoardPresets.EASY`, `.HARD`, `.NIGHTMARE`. Different ratios.

### 10. Ranked vs casual
Ranked games persist; casual are ephemeral. Same engine; different storage policy.

## Tradeoffs

### Sealed interface vs abstract class for Jumper

| Criterion | Sealed interface | Abstract class |
| --- | --- | --- |
| Exhaustive switch | yes (Java 21+) | yes via pattern |
| Multiple inheritance | implementation only | no |
| Field reuse | none (records) | possible |
| Decision | **Sealed interface + records** ✓ |

Records also give us value-equality, `toString`, `hashCode` for free.

### Enum + guards vs full State pattern for Game.status

| Criterion | Enum + guards | State subclasses |
| --- | --- | --- |
| LoC | tiny | larger |
| Per-state behavior | low | high |
| Decision | **Enum + guards** ✓ — Game has 3 states with simple guards. State pattern is overkill. |

### Synchronous vs async listeners

| Criterion | Sync | Async (queue) |
| --- | --- | --- |
| Latency | bounded by slowest listener | bounded by enqueue |
| Ordering | guaranteed | needs care |
| V1 fit | great | overkill |
| Decision | **Sync for V1**; async behind a flag for V2. |

### Mutable Player vs immutable Player + new instance per move

| Criterion | Mutable | Immutable |
| --- | --- | --- |
| Memory (per move) | O(1) | O(N) refs |
| Reasoning | "who can mutate?" | "history is replay" |
| Replay | tricky | trivial (just an event log) |
| Decision | **Mutable, but package-private mutators only** ✓ — easier than full event sourcing for V1. |

### Exact-finish vs win-on-overshoot

This is a **gameplay** choice, not an engineering one. We expose both as configurable rule packs.

## Open questions (interview answer: "I'd ask")

- Should two players be able to share a cell? (Default: yes; could be configurable.)
- Bonus roll on max? (Common variant.)
- How long does a turn timeout in V2? (UX call.)
- Are bots allowed in casual matches? (Product call.)

## Output

```
Extensions:    multi-game server, powerups, bots, replay, top-3 finish,
               custom jumpers, animated client, difficulty presets
Pre-decided:   sealed Jumper, enum+guards for status, sync listeners V1,
               mutable Player with package-private mutators
Open Qs:       cell sharing, bonus rolls, timeouts, bots
```
