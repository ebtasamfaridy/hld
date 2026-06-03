# 07 · Snake and Ladder

> Classic LLD interview problem. Tests whether you can model a simple game cleanly: extensible board entities, configurable dice, multiple players, deterministic state transitions, and a clean game loop.

## What you will master

- Modeling **board entities** (Snake, Ladder) with a common abstraction so adding teleporters/portals later is trivial.
- **Strategy** for dice (single die, multi-die, biased die, crooked die for tests).
- **State pattern** at the game level (`Waiting → InProgress → Finished`) and at the player level (`Active → Won → Disqualified`).
- **Observer/Listener** for game events (move, snake bite, ladder climb, win).
- **Validation rules** as a Chain of Responsibility (start-condition rule, exact-finish rule, etc.).
- A clean **game loop** that doesn't leak state.
- How to make the game testable without random dice (inject a deterministic dice).

## Read order

| # | File |
| - | --- |
| 1 | [01_requirements.md](./01_requirements.md) |
| 2 | [02_capacity_estimation.md](./02_capacity_estimation.md) |
| 3 | [03_hld.md](./03_hld.md) |
| 4 | [04_domain_model.md](./04_domain_model.md) |
| 5 | [05_database_design.md](./05_database_design.md) |
| 6 | [06_api_design.md](./06_api_design.md) |
| 7 | [07_class_diagrams.md](./07_class_diagrams.md) |
| 8 | [08_sequence_diagrams.md](./08_sequence_diagrams.md) |
| 9 | [09_state_machines.md](./09_state_machines.md) |
| 10 | [10_design_patterns.md](./10_design_patterns.md) |
| 11 | [11_concurrency_and_scaling.md](./11_concurrency_and_scaling.md) |
| 12 | [12_machine_coding_skeleton/](./12_machine_coding_skeleton/) |
| 13 | [13_extensions_and_tradeoffs.md](./13_extensions_and_tradeoffs.md) |
| 14 | [14_interviewer_followups.md](./14_interviewer_followups.md) |

## Headline tradeoffs

- Snake and Ladder are **the same abstraction** (`Jumper` — moves you to another cell). Don't model them as two distinct subclasses with duplicated logic.
- Dice should be a **strategy**, not `Math.random()` scattered through the code. Otherwise you can't unit-test win conditions.
- The **game loop owns turn order**, not the player. Players are passive participants; the game polls them.
- **Validation rules** (must roll 6 to start, exact roll to finish) are pluggable — different rule packs are different games.
