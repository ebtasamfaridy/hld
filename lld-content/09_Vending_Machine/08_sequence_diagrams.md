# 08 · Vending Machine — Sequence Diagrams

## 1. Successful purchase

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant VM as VendingMachine
    participant S as State
    participant I as Inventory
    participant E as EscrowedPayment
    participant C as ChangeMaker
    participant H as Hardware

    U->>VM: selectProduct(A1)
    VM->>S: state.selectProduct(machine, A1)
    S->>I: getSlot(A1)
    I-->>S: Slot{count=5, price=₹35}
    S->>VM: transitionTo(ProductSelectedState)
    Note over VM: state = ProductSelected

    loop until total >= 35 OR cancel
      U->>VM: insertCoin(₹10)
      VM->>S: state.insertCoin(machine, ₹10)
      S->>E: add(₹10)
      Note over S: if first coin, state = AcceptingPayment
    end

    Note over E: total=₹40, price=₹35, change=₹5
    S->>C: makeChange(₹5, cashFloat)
    C-->>S: { ₹5: 1 }
    S->>VM: transitionTo(DispensingState)
    VM->>I: decrement(A1)
    VM->>H: dispense(A1)
    VM->>H: returnCoins({₹5: 1})
    VM->>E: clear()                 (consumed by cash)
    Note over VM: cash += {₹10:1, ₹10:1, ₹10:1, ₹10:1} then cash -= {₹5:1}
    VM->>VM: transitionTo(IdleState)
```

## 2. Cancel mid-payment

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant VM as VendingMachine
    participant S as State (AcceptingPayment)
    participant E as EscrowedPayment
    participant H as Hardware

    U->>VM: insertCoin(₹10)
    U->>VM: insertCoin(₹10)
    Note over E: total=₹20

    U->>VM: cancel()
    VM->>S: state.cancel(machine)
    S->>H: returnCoins({₹10: 2})
    S->>E: clear()
    S->>VM: transitionTo(IdleState)
```

## 3. Cannot make change

```mermaid
sequenceDiagram
    autonumber
    participant VM as VendingMachine
    participant S as State (AcceptingPayment)
    participant C as ChangeMaker
    participant E as Escrow
    participant H as Hardware

    Note over E: total=₹100, price=₹35, change=₹65
    S->>C: makeChange(₹65, cashFloat)
    C-->>S: Optional.empty()
    Note over S: refund and stay in Idle (do NOT dispense)
    S->>H: returnCoins({₹100: 1})
    S->>E: clear()
    S->>VM: transitionTo(IdleState)
    VM->>VM: emit ChangeUnavailable event
```

## 4. Hardware fails mid-dispense

```mermaid
sequenceDiagram
    autonumber
    participant VM as VendingMachine
    participant H as Hardware
    participant A as AuditLog

    VM->>H: dispense(A1)
    H--xVM: HardwareError("motor jam")
    VM->>VM: transitionTo(MaintenanceState)
    VM->>A: log(MAINTENANCE_ENTERED + HARDWARE_ERROR)
    Note over VM: do NOT auto-refund — product may have partially dispensed
    Note over VM: operator app shows alert, intervenes manually
```

This is intentional. Auto-refunds on hardware errors are how you give people free items.

## 5. Operator refill

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operator
    participant VM as VendingMachine
    participant I as Inventory
    participant CI as CashInventory

    Op->>VM: enterMaintenance(operatorId)
    VM->>VM: state = MaintenanceState
    Op->>VM: refillSlot(A1, +10, ₹35)
    VM->>I: refill(A1, +10, ₹35)
    Op->>VM: refillCash(₹10, +20)
    VM->>CI: add({₹10: 20})
    Op->>VM: collectCash()
    VM->>CI: drain → returns Money
    Op->>VM: exitMaintenance()
    VM->>VM: state = IdleState
```

## 6. Power-loss recovery on reboot

```mermaid
sequenceDiagram
    autonumber
    participant VM as VendingMachine (booting)
    participant DB as SQLite
    participant Op as Operator (alert)

    VM->>DB: load inventory + cash + audit_log tail
    alt last event is committed (e.g., DISPENSE_OK)
        VM->>VM: state = IdleState
    else last event indicates partial txn
        VM->>VM: state = MaintenanceState
        VM->>Op: emit MaintenanceAlert{reason="recovery"}
    end
```

## Output

```
Happy:        select → insert*N → makeChange → decrement → dispense → returnCoins → Idle
Cancel:       returnCoins(escrow) → Idle
No-change:    refund → Idle (do NOT dispense)
HW error:     Maintenance + alert (no auto-refund)
Operator:     enterMaintenance → refill/collect → exitMaintenance
Recovery:     replay audit_log; alert if partial
```
