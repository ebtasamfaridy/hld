# 04 · Vending Machine — Domain Model

## Aggregates

```text
VendingMachine (root)
├── Inventory                    # slot → (product, count, price)
│   └── Map<SlotCode, Slot>
├── CashInventory                # denomination → count
│   └── Map<Denomination, Integer>
├── EscrowedPayment              # inserted but not committed
│   ├── Map<Denomination, Integer> coins
│   └── Money totalInserted
├── State (subclass)             # current state object
├── SelectedSlot? (transient)
└── List<AuditEvent>

Slot (record)
├── SlotCode (e.g., "A1")
├── Product (id, name)
├── Money price
└── int count

Money
├── long minorUnits              # paise / cents
└── Currency

Denomination (record)
├── Money value
└── DenominationKind { COIN, NOTE }
```

## State (subclass per state)

```java
public sealed interface State permits IdleState, ProductSelectedState,
                                      AcceptingPaymentState, DispensingState,
                                      MaintenanceState, OutOfServiceState {

    default void selectProduct(VendingMachine m, SlotCode s) { reject("selectProduct"); }
    default void insertCoin(VendingMachine m, Denomination d) { reject("insertCoin"); }
    default void cancel(VendingMachine m) { reject("cancel"); }
    default void enterMaintenance(VendingMachine m) { reject("enterMaintenance"); }

    private void reject(String op) {
        throw new IllegalStateException(op + " not valid in " + getClass().getSimpleName());
    }
}
```

Each subclass overrides only the operations it permits.

```java
public final class IdleState implements State {
    @Override public void selectProduct(VendingMachine m, SlotCode s) {
        m.requireInStock(s);
        m.setSelectedSlot(s);
        m.transitionTo(new ProductSelectedState());
    }
    @Override public void enterMaintenance(VendingMachine m) {
        m.transitionTo(new MaintenanceState());
    }
}

public final class ProductSelectedState implements State {
    @Override public void insertCoin(VendingMachine m, Denomination d) {
        m.escrow(d);
        m.transitionTo(new AcceptingPaymentState());
    }
    @Override public void cancel(VendingMachine m) {
        m.clearSelection();
        m.transitionTo(new IdleState());
    }
}

public final class AcceptingPaymentState implements State {
    @Override public void insertCoin(VendingMachine m, Denomination d) {
        m.escrow(d);
        if (m.escrowMeetsOrExceedsPrice() && m.canMakeChange()) {
            m.transitionTo(new DispensingState());
            m.dispenseSelected();   // commit
        }
    }
    @Override public void cancel(VendingMachine m) {
        m.refundEscrow();
        m.clearSelection();
        m.transitionTo(new IdleState());
    }
}

public final class DispensingState implements State {
    // No customer operations valid here. Auto-transition to Idle on success
    // or Maintenance on hardware error.
}
```

## Why subclasses, not enum + ifs

Enum + ifs requires editing every method on every state addition. Subclass per state localizes change.

Trade: 5 small classes vs 1 medium one. For interview clarity, the explicit shape wins.

## ChangeMaker — algorithm note

```java
public interface ChangeMaker {
    /** Returns the multiset of denominations summing to `amount`, or empty if not feasible. */
    Optional<Map<Denomination, Integer>> makeChange(Money amount, Map<Denomination, Integer> available);
}
```

**Greedy** by largest denomination works for **canonical** denominations (1, 2, 5, 10, 20, 50, 100, 500). It does NOT work for arbitrary sets (classic counterexample: denominations {1, 3, 4}, target 6 → greedy picks 4+1+1, optimal is 3+3).

Indian / US currency is canonical, so greedy is fine. We document this and provide a DP fallback for safety:

```java
// DP for non-canonical denominations
int[] dp = new int[amount + 1];
Arrays.fill(dp, Integer.MAX_VALUE);
dp[0] = 0;
for (int a = 1; a <= amount; a++)
    for (Denomination d : denoms)
        if (a >= d.value() && dp[a - d.value()] != Integer.MAX_VALUE)
            dp[a] = Math.min(dp[a], dp[a - d.value()] + 1);
```

Track parent choices to reconstruct the multiset; check that each denomination's required count ≤ available.

## Money type

Always **integer minor units** (paise). Never `double`. Otherwise `0.10 + 0.20 != 0.30` ruins your day.

```java
public record Money(long minorUnits, Currency currency) implements Comparable<Money> { ... }
```

## Domain events (audit)

```
- ProductSelected(slot, ts)
- CoinInserted(denom, ts)
- ProductDispensed(slot, txnId, ts)
- ChangeReturned(amount, denoms, ts)
- TransactionCancelled(reason, ts)
- MaintenanceEntered(reason, ts)
- OperatorRefilled(slot, deltaCount, ts)
- OperatorCollected(amountCollected, ts)
- HardwareError(component, message, ts)
```

Append-only. Each event has a monotonic `seq` for ordering.

## Output

```
Aggregates:    VendingMachine, Inventory, CashInventory
Value objects: Slot, Money, Denomination, AuditEvent
States:        sealed State subclasses (Idle, ProductSelected, AcceptingPayment, Dispensing, Maintenance, OutOfService)
Algorithms:    Greedy change-making (canonical) + DP fallback
```
