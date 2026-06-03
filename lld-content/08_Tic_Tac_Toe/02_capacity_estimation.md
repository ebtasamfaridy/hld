# 02 · Tic Tac Toe — Capacity Estimation

## Single game

Trivial. 9 cells, ≤9 moves, sub-millisecond per move.

## Multi-tenant server (V2)

Same playbook as `07_Snake_and_Ladder`:
```
Concurrent games:    100 K
Memory per game:     ~2 KB (smaller board, smaller history)
Moves per second:    13 K (similar order)
Sticky-routed actor per game.
```

## Bot training scale (V2)

If we train an RL agent on this game:
```
Self-play:    1 M games / hour
Moves:        ~9 / game → 9 M moves/hour ≈ 2.5 K moves/sec
              (single core can do >100 K moves/sec because each is O(1))
```

The win-detection algorithm is the bottleneck of bot training. A naive O(N²) check turns 100 K moves/sec into 1 K moves/sec — drastically slower training. **O(1) incremental win detection earns its keep here.**

## Storage

```
Move log per game: ~9 moves × 16 B = 144 B
Game record:       ~200 B
1 M completed games:  ~200 MB
```

Negligible.

## Output

```
Single game:    trivial
V2 server:      100 K games × 2 KB = ~200 MB; sticky-routed
Bot training:   O(1) win-detection is mandatory; otherwise ~10× slower
```
