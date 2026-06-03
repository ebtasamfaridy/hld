# 11 · Tic Tac Toe — Concurrency & Scaling

## V1 — single-threaded
Same as Snake & Ladder: sequential loop, no locks.

## V2 — multi-tenant server

### Sticky-routed actor per game
- WebSocket hub hashes `game_id` → backend pod.
- One single-threaded executor per game.
- Persist after each move with optimistic version.

### Idempotency
`client_seq` per (game, player); cached last-applied response.

### Crash recovery
Rehydrate from the document store; clients reconnect with their last seen `seq`.

## Bot self-play scaling

For RL training:
- **Multi-threaded** is fine because each game is independent.
- The expensive bit is the move computation, not Board ops.
- One thread per CPU; work-stealing scheduler.
- Use **object pools** for Board to avoid GC pressure.

```
1 thread × 100 K games / hour × 9 moves = ~250 moves/sec.
8 threads → 2 K moves/sec.
With Board pooling and JIT-friendly inner loops → 50 K moves/sec.
```

## Why we don't multi-thread *within* a game
- Win detection is O(1) — no work to parallelize.
- Move ordering must be sequential.
- Multi-threading would add lock overhead with zero benefit.

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Pod crash | Rehydrate from doc store; client reconnects |
| Slow bot | Time-budget per move; fall back to random |
| Bot infinite loop | Hard timeout; disqualify or switch to human |
| Concurrent move attempts | "Not your turn" rejected; `client_seq` dedup |

## Capacity at 100 K concurrent games (web-hosted)

```
Memory:    100 K × 2 KB = 200 MB
DB writes: ~13 K/sec (similar to Snake & Ladder)
WebSocket conns: 200 K (2 per game)
Pods:      ~3 (sticky-routing)
```

## Output

```
V1:    single-threaded, no concurrency
V2:    sticky-routed actor per game
RL:    parallelize across games, not within
Bot:   time-budgeted; fall back on timeout
```
