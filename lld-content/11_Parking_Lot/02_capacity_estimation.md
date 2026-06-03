# 02 · Parking Lot — Capacity Estimation

## Single lot

```
Spots:                    5 000 (large airport)
Daily entries:           10 000 (avg 2 vehicles/spot/day)
Peak gate RPS:               20 (entries + exits in rush)
Audit:                  ~50 K events/day × 1 KB = 50 MB/day
DB working set:        ~1 MB (live tickets) hot
Read RPS (dashboard):    ~10 RPS internal
```

## Per-region operator (V2)

```
Lots:                    1 000 (city operator)
Spots total:           ~500 K
Daily entries:         ~2 M
Backend RPS:           ~50 RPS sustained, 200 peak
Storage:                ~2 GB / day audit
```

## Forces on the design

1. **Allocation latency must be tight** — driver hand at gate. < 50 ms.
2. **Multiple concurrent gates** at one lot — atomic claim.
3. **Audit is durable, async to upload** — write to local SQL/Postgres immediately.
4. **Dashboard reads** are low-volume; no caching needed for V1.
5. **Reservations** require a separate calendar model with hold / commit semantics — the same problem as Hotel Booking, applied to parking.

## Hot path

```
Vehicle arrives →
  EntryGate.requestEntry(plate, vehicleType) →
    AllocationStrategy.pick(vehicleType) →
      atomic claim →
        Ticket persisted →
          gate opens
```

End-to-end target: 50 ms p99.

## Output

```
Single lot:    5 000 spots, 10 K entries/day, 20 peak RPS, 50 MB/day audit
Multi-lot:     1 K lots, 500 K spots, 2 M entries/day, 50–200 RPS, 2 GB/day audit
Forces:        sub-50 ms allocation, atomic concurrent claim, durable audit
```
