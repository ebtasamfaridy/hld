# 10 · Vending Machine — Design Patterns

## 1. State pattern (the headline)

**Problem.** Different operations are valid in different states; spreading `if (state == ...)` checks across every method is brittle.

**Pattern.** `sealed interface State` with subclasses for each state. Each subclass overrides the operations it permits; default methods reject otherwise.

**Tradeoff.** 6 small classes. Worth it: localizes change. Adding a new state (e.g., `ScheduledMaintenanceState`) doesn't touch other states.

## 2. Strategy — `ChangeMaker`

**Problem.** Greedy works for canonical denominations; we may need DP for non-canonical. Don't force one algorithm.

**Pattern.** `ChangeMaker` interface. Plug `GreedyChangeMaker` (default) or `DpChangeMaker` (for unusual denoms).

## 3. Strategy — `HardwareAdapter`

**Problem.** Real hardware in production, stub in tests, simulator in dev.

**Pattern.** `HardwareAdapter` interface. `StubHardware`, `SimulatedHardware`, `MockHardware`.

## 4. Builder — `VendingMachine.Builder`

Construction with many components; builder validates wiring.

## 5. Observer — `AuditListener`

Every event (coin inserted, dispense, error) goes to listeners; an `AuditListener` writes to SQLite, a `MetricsListener` updates counters, etc.

## 6. Singleton (controlled) — `VendingMachine`

A physical machine has exactly one controller. We don't "newPattern Singleton"; we just instantiate once at boot. Tests use multiple instances.

## 7. Memento — escrow as a transactional snapshot

`EscrowedPayment` is the transient "what's in flight." On cancel, we restore the customer's money from this snapshot. The snapshot **is** the memento; we don't formalize it further.

## 8. Repository — `Inventory`, `CashInventory`, `AuditLog`

Each owns its persistence concerns. Domain talks to interfaces.

## 9. Chain of Responsibility (lite) — operator validators

Refill / setPrice / collect each pass through validators (operator authed? machine in maintenance? amount sane?). A small validator chain keeps preconditions explicit.

## What we avoid

| Pattern | Why not |
| --- | --- |
| Visitor over State | Sealed switch + state methods are cleaner |
| Prototype for Slots | Slots are value-y, just `record` |
| Singleton enforced via static instance | Premature for tests |

## Pattern table

| Pattern | Where | What it solves |
| --- | --- | --- |
| State | `state/*` | Operation legality per state |
| Strategy | ChangeMaker, HardwareAdapter | Pluggable algorithm / driver |
| Builder | VendingMachine.Builder | Readable construction |
| Observer | AuditListener | Decouple side effects |
| Memento (lite) | EscrowedPayment snapshot | Cancel/refund |
| Repository | Inventory, CashInventory, AuditLog | DB abstraction |
| CoR (lite) | Operator validators | Ordered preconditions |

## Output

State pattern is the centerpiece. Strategy slots in for the algorithmic choice (change-making) and the driver (hardware). Everything else is supporting cast.
