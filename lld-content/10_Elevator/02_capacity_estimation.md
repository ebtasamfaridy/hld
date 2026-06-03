# 02 · Elevator System — Capacity Estimation

A single building's elevator system is small. Scale shows up if we model **building-management software** for many buildings.

## Single building (40 floors, 6 cars)

```
Floors:               40
Cars:                  6
Hall presses peak:    20 / minute   (rush hour)
Car presses peak:     30 / minute
Tick rate per car:    10 Hz         (smoothness)
Memory per car:       ~5 KB
Memory total:         ~30 KB
Dispatch CPU:         ~10 K decisions / day → micro
Audit per day:        ~5 K events × 200 B = 1 MB
```

A single CPU core easily handles the full building. Real elevator systems run on embedded controllers per car + one group controller.

## Building-management SaaS (V2)

```
Buildings:            10 K
Cars per building:     6 (avg)
Total cars:           60 K
Audit RPS:           ~7 K (ingestion: status, alarms)
Real-time dashboards: ~1 K concurrent operators
```

Now we have a backend ingesting 60 K device streams. The architecture is:
- per-building edge controller (the V1 we design),
- aggregation gateway,
- cloud audit + dashboards.

## What forces the design

1. **Soft real-time per-car simulation.** 10 Hz tick rate is tight; we don't want GC pauses. Per-car logic is O(1).
2. **Dispatch decisions are infrequent** (hundreds per minute) but latency-sensitive (< 100 ms).
3. **Audit is durable + async** (same as Vending).
4. **Building isolation**: each building's controller is independent.

## Local state (per building)

```
in-memory:    car states, hall call queue, dispatcher
local DB:     audit log
```

## Output

```
Single building:    trivial; embedded compute
SaaS:               60 K cars, 7 K RPS audit ingest, building-isolated controllers
Constraint:         soft real-time 10Hz per car; sub-100ms dispatch
```
