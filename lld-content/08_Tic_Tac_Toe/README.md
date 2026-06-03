# 08 · Tic Tac Toe

> The smallest of the LLD problems, but a deceptively rich design exercise. The trap: candidates write a procedural mess. The win: a clean domain model that **generalizes to N×N**, supports custom symbols and bots, and runs in O(1) per move via incremental win-detection.

## What you will master

- Modeling **Board**, **Symbol**, **Move**, **GameStatus** as small, focused objects.
- O(1) **incremental win detection** (counters per row/col/diagonal), not O(N²) full scan per move.
- **Strategy** for player input (Human, Bot via Minimax, Bot via Random).
- N×N generalization with K-in-a-row variant (Gomoku is just K=5 on a 15×15).
- Game state machine: `WAITING → IN_PROGRESS → X_WON | O_WON | DRAW`.
- Listener / Observer for game events.

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

- **O(1) win detection per move** beats the naïve full-scan; matters at large N or for high-throughput bot training.
- **Generalize from day 1** to (N×N, K-in-a-row). 3×3-only code costs the same to write but doesn't scale.
- **Players are pluggable input sources** — Human or Bot — sharing the same `Player` type.
- **Boards are immutable snapshots from the bot's perspective** but the live game holds a mutable Board for performance. We bridge with `Board.snapshot()`.
