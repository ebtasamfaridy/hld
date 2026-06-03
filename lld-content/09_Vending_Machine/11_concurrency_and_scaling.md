# 11 · Vending Machine — Concurrency & Scaling

## Concurrency on a single machine

A single machine is a sequential device — one customer at a time. The "concurrency" is between:

| Threads | Race |
| --- | --- |
| UI thread | reading state to render |
| Hardware thread | coin/note acceptor pushes events |
| Timeout thread | scheduled reset on idle |
| Operator app thread | (rare) puts machine in maintenance mid-flow |

### Single-threaded controller

We funnel **all** events through one event loop / actor on the controller. UI raises an event; hardware raises an event; timeout raises an event. The loop processes them sequentially, mutating state.

This is the same pattern as the Snake-and-Ladder game actor: **single-threaded by design** so we never need locks within the machine.

```java
public final class VendingMachine {
    private final BlockingQueue<MachineEvent> events = new LinkedBlockingQueue<>();
    private final Thread loop = new Thread(this::runLoop);
    // start the loop on boot

    private void runLoop() {
        while (running) {
            MachineEvent e = events.take();
            try { dispatch(e); } catch (Throwable t) { handleError(t); }
        }
    }

    public void selectProduct(SlotCode s) { events.put(new SelectProductEvent(s)); }
    // hardware events similarly
}
```

### Operator vs customer

If an operator presses "enter maintenance" while a customer is paying, two options:
1. **Reject** — "machine busy." Operator waits.
2. **Force** — refund the customer, transition to maintenance.

We choose **reject** in V1; operator waits. Documented; configurable in V2.

## Scaling — fleet (V2)

Each machine is independent. Backend concerns:

| Workload | Pattern |
| --- | --- |
| Heartbeats | UDP / lightweight HTTP; aggregate in time-series DB (Prometheus / TSDB) |
| Audit upload | batch HTTP; idempotent on `(machine_id, seq)` |
| Remote command (restart) | poll-based or WebSocket; pending command queue per machine |
| Reporting | OLAP query against partitioned audit table |

50 K machines × 1 heartbeat/min = ~833 RPS sustained — trivial.

5 M txns/day × 5 events/txn = 25 M audit rows/day. Partition monthly (~1 GB/partition). Same playbook as Streak's `daily_activity`.

## Failure modes

| Failure | Mitigation |
| --- | --- |
| Coin acceptor stuck "INSERTING" | 60s timeout, refund |
| Note acceptor jam | Maintenance state |
| Network down (fleet) | Local audit only; sync on reconnect |
| Power loss | Replay audit on reboot; alert on partial txn |
| Cash float overflows (operator forgot to collect) | Soft cap; operator alert |
| Cash float zero on coin denom | Alert; refuse change-requiring purchases |
| Operator app stale | If-Match version on PATCHes |
| Card gateway timeout | Treat as decline |

## What we explicitly avoid

- **Distributed locks per machine.** No need; a machine is one process.
- **Multi-writer cash float.** Single thread mutates; no need for atomics.
- **Cross-machine atomic operations.** Out of scope for V2 too.

## Backpressure on audit upload

If a machine has been offline for a week and has 5 K events to upload:
- Cap batch size at 500 events.
- Server returns `next_seq`; client resumes from there.
- Don't drop events on the machine until the server has acked them.

## Output

```
Per-machine:    single-threaded event loop; all events serialized
Operator race:  reject mid-flow operator commands
Fleet scaling:  5 M txn/day; 833 RPS heartbeats; audit partitioned monthly
Failure:        hardware errors → Maintenance + alert; no auto-refund
```
