# 02 · Snake and Ladder — Capacity Estimation

A single game is trivially small. The interesting estimation is when this becomes a **multi-tenant game server** (V2 / extension).

## Single game

```
Cells:                100
Snakes:               ~10 max
Ladders:              ~10 max
Players:              2..6
Move history per player: ~50 moves (avg game length)
Memory per game:      ~5 KB
Time per move:        sub-millisecond (trivial array lookup + math)
```

For a local interview, capacity is irrelevant. Mention this and pivot to logical correctness.

## Multi-tenant server (V2)

If you run a hosted Snake & Ladder service:

```
Concurrent games:     100 K
Memory per game:      ~5 KB → 500 MB total
Active players:       100 K × 4 = 400 K
Moves per minute:     400 K / 30 s = ~13 K moves/sec
Network ops:          dice roll → broadcast move → listen for next dice
```

This becomes a **stateful WebSocket service**. Each game is a small actor; one instance can host ~10 K games per core.

```
Game instances per pod (4-core):   ~40 K
Pods needed:                       ~3 (with headroom)
```

Persistence is optional — a game can live entirely in memory and be lost on disconnect (a la a chess.com casual game). For ranked play, persist after each move (~13 K writes/sec to a key-value store, easily handled by Redis cluster or DynamoDB).

## Output

```
V1 (in-memory):    trivial — single-game memory < 10 KB, micro-second moves
V2 (server):       100 K games × 5 KB = 500 MB memory; ~13 K moves/sec;
                   sticky-session WebSockets; optional persistence per move
```

The takeaway: **capacity is not the hard part of this problem.** Modeling is.
