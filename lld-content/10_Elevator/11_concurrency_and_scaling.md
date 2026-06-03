# 11 · Elevator System — Concurrency & Scaling

## Threading model

```
Building thread:    receives hall/car presses, runs dispatcher, updates audit
Per-car thread:     ticks at 10 Hz; advances state, drives motors / doors
Listener thread:    fans out events to dashboards / cloud sync
```

Synchronization:
- **Per-car state is owned by its thread.** Dispatcher posts `addStop(floor)` via a thread-safe queue (e.g., `LinkedBlockingDeque` per car).
- **Dispatcher reads car state** via a `volatile` snapshot (`floor, direction, status, load, stop count`) updated by the car. Eventual consistency is fine for cost function — slightly stale info is OK.
- **Audit log writes** are append-only; sync via the `BuildingListener` thread.

## Why not single-threaded?

Each car must tick at 10 Hz and drive hardware in real time. Contending for a single thread means tick latency. Independent threads + lock-free interfaces deliver bounded latency.

For the V1 simulator, we can run single-threaded with a virtual clock — that's a separate concern from production.

## Scaling — multi-building V2

Each building is independent. The cloud backend is read-heavy:
- Heartbeats from buildings: ~1/min/building × 10 K = 167 RPS.
- Audit ingest: 60 K cars × 5 events/min = 5 K RPS.
- Dashboard reads: time-series queries; small.

Architecture:
- Edge controller per building (the V1).
- Each emits to Kafka for cloud audit.
- Postgres for building/car registry; time-series DB for metrics.

## Hot paths

| Path | Latency target | Mitigation |
| --- | --- | --- |
| Hall press → car assignment | < 100 ms | In-memory cost function; no DB |
| Car tick | 100 ms | Pre-allocated structures; no GC in tick |
| Audit emit | async | Buffered; never blocks tick |
| Operator command | < 500 ms | Async; ack at receipt; effect on next tick |

## Failure modes

| Failure | Mitigation |
| --- | --- |
| One car thread crash | Dispatcher excludes it; alarm; restart |
| Dispatcher crash | Hot-standby; in-flight hall calls re-dispatched |
| Network partition cloud | Local audit only; sync later |
| Power loss | Cars stop at next floor; doors open; resume on power |
| Door fault | Car OUT_OF_SERVICE; hall calls reassigned |
| All cars OUT_OF_SERVICE | Hall calls rejected with `503` |
| Earthquake / EVAC | All cars to ground; ignore everything |

## Backpressure

Hall press storms (rush hour):
- Dispatcher absorbs; never blocks.
- Cost function is O(M) per call (M = car count, ≤ 10).
- 100 calls / sec is trivial.

## Capacity at SaaS scale (V2)

```
10 K buildings × 6 cars × 1 event / 5 sec = 12 K events / sec audit ingest
167 RPS heartbeats
500 concurrent dashboard users
```

Postgres + Kafka + a TS-DB cluster handle this comfortably.

## Output

```
V1:    one thread per car (10 Hz tick) + dispatcher thread + listener thread
       lock-free state-snapshot reads; per-car queue for assigns
V2:    edge controllers per building; Kafka → cloud; TS-DB for metrics
```
