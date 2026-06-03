# 07 · Vending Machine — Class Diagrams

## Aggregated class diagram

A single view of every class, interface, and enum in the system — fields and methods included. Source of truth: the Java skeleton under `12_machine_coding_skeleton/`. This is the one LLD system that uses the **GoF State pattern** (each state is its own class implementing `State`); the diagram makes that explicit.

```mermaid
classDiagram
    %% ===== Value objects & enums =====
    class Money {
      <<value type>>
      -long minor
      -String currency
      +inr(rupees) Money$
      +zero() Money$
      +plus(other) Money
      +minus(other) Money
      +isZero() boolean
      +isNegative() boolean
      +amountMinor() long
    }
    class Denomination {
      <<enumeration>>
      ONE_RUPEE
      FIVE_RUPEE
      TEN_RUPEE
      TWENTY_RUPEE
      FIFTY_RUPEE
      HUNDRED_RUPEE
      +value() Money
    }
    class SlotCode {
      <<value type>>
      -String code
      +of(s) SlotCode$
      +toString() String
    }
    class Product {
      <<record>>
      -String id
      -String name
    }

    %% ===== Domain mutables =====
    class Slot {
      -SlotCode code
      -Product product
      -Money price
      -int count
      +setPrice(p)
      +increment(delta)
      +tryDecrement() boolean
      +code() SlotCode
      +price() Money
      +count() int
    }
    class EscrowedPayment {
      -Map~Denomination,Integer~ coins
      -Money total
      +add(denomination)
      +clear()
      +snapshotCoins() Map~Denomination,Integer~
      +total() Money
      +isEmpty() boolean
    }
    Slot o-- "1" Product
    Slot o-- "1" Money
    EscrowedPayment o-- "*" Denomination

    %% ===== Inventory =====
    class Inventory {
      -Map~SlotCode,Slot~ slots
      +add(slot)
      +get(code) Optional~Slot~
      +isInStock(code) boolean
      +decrement(code) boolean
      +refill(code, units)
      +slots() Collection~Slot~
    }
    class CashInventory {
      -Map~Denomination,Integer~ coins
      +deposit(denomination)
      +deposit(coins)
      +withdraw(denomination, n)
      +snapshot() Map~Denomination,Integer~
      +totalValue() Money
    }
    Inventory o-- "*" Slot

    %% ===== Strategy: ChangeMaker =====
    class ChangeMaker {
      <<interface>>
      +makeChange(amount, available) Optional~Map~
    }
    class GreedyChangeMaker {
      +makeChange(amount, available) Optional~Map~
    }
    ChangeMaker <|.. GreedyChangeMaker

    %% ===== Hardware adapter =====
    class HardwareAdapter {
      <<interface>>
      +dispense(slotCode)
      +returnCoins(coins)
      +displayMessage(msg)
    }
    class StubHardware {
      -PrintStream out
      +dispense(slotCode)
      +returnCoins(coins)
      +displayMessage(msg)
    }
    HardwareAdapter <|.. StubHardware

    %% ===== Audit / Observer =====
    class AuditListener {
      <<interface>>
      +log(event, detail)
    }
    class ConsoleAuditListener {
      -PrintStream out
      -Clock clock
      +log(event, detail)
    }
    AuditListener <|.. ConsoleAuditListener

    %% ===== State pattern (GoF) =====
    class State {
      <<sealed interface>>
      +selectProduct(m, slot)
      +insertCoin(m, denomination)
      +cancel(m)
      +enterMaintenance(m)
      +exitMaintenance(m)
      +name() String
    }
    class IdleState {
      +selectProduct(m, slot)
      +enterMaintenance(m)
      +name() String
    }
    class ProductSelectedState {
      +insertCoin(m, denomination)
      +cancel(m)
      +name() String
    }
    class AcceptingPaymentState {
      +insertCoin(m, denomination)
      +cancel(m)
      +name() String
    }
    class DispensingState {
      +name() String
      +complete(m)
    }
    class MaintenanceState {
      +exitMaintenance(m)
      +name() String
    }
    State <|.. IdleState
    State <|.. ProductSelectedState
    State <|.. AcceptingPaymentState
    State <|.. DispensingState
    State <|.. MaintenanceState

    %% ===== Holder / orchestrator =====
    class VendingMachine {
      -Inventory inventory
      -CashInventory cash
      -ChangeMaker changeMaker
      -HardwareAdapter hardware
      -AuditListener audit
      -Clock clock
      -State state
      -EscrowedPayment escrow
      -SlotCode selected
      +selectProduct(slot)
      +insertCoin(denomination)
      +cancel()
      +enterMaintenance()
      +exitMaintenance()
      +refill(slotCode, n)
      +setPrice(slotCode, price)
      +depositCash(coins)
      +isInStock(slot) boolean
      +setSelectedSlot(slot)
      +transitionTo(state)
      +audit(event, detail)
      +state() State
    }
    VendingMachine o-- "1" Inventory
    VendingMachine o-- "1" CashInventory
    VendingMachine o-- "1" ChangeMaker
    VendingMachine o-- "1" HardwareAdapter
    VendingMachine o-- "1" AuditListener
    VendingMachine o-- "1" State
    VendingMachine *-- "1" EscrowedPayment
```

---



## Class diagram

```mermaid
classDiagram
    class VendingMachine {
      -Inventory inventory
      -CashInventory cash
      -EscrowedPayment escrow
      -State state
      -SlotCode selectedSlot
      -ChangeMaker changeMaker
      -HardwareAdapter hardware
      -AuditLog audit
      +browse() InventoryView
      +selectProduct(slot)
      +insertCoin(denom)
      +cancel()
      +state() State
    }

    class State {
      <<sealed>>
      +selectProduct(machine, slot)
      +insertCoin(machine, denom)
      +cancel(machine)
    }
    class IdleState
    class ProductSelectedState
    class AcceptingPaymentState
    class DispensingState
    class MaintenanceState
    class OutOfServiceState
    State <|.. IdleState
    State <|.. ProductSelectedState
    State <|.. AcceptingPaymentState
    State <|.. DispensingState
    State <|.. MaintenanceState
    State <|.. OutOfServiceState

    class Inventory {
      -Map~SlotCode, Slot~ slots
      +getSlot(code) Slot
      +decrement(code)
      +refill(code, delta, price)
    }

    class CashInventory {
      -Map~Denom, int~ counts
      +addAll(map)
      +remove(map)
      +canMake(amount) boolean
      +totalValue() Money
    }

    class EscrowedPayment {
      -Map~Denom, int~ coins
      +add(d)
      +clear()
      +total() Money
    }

    class ChangeMaker {
      <<interface>>
      +makeChange(amount, available) Optional~Map~
    }
    class GreedyChangeMaker
    class DpChangeMaker
    ChangeMaker <|.. GreedyChangeMaker
    ChangeMaker <|.. DpChangeMaker

    class HardwareAdapter {
      <<interface>>
      +dispense(slot)
      +returnCoins(map)
    }
    class StubHardware
    HardwareAdapter <|.. StubHardware

    VendingMachine o-- Inventory
    VendingMachine o-- CashInventory
    VendingMachine o-- EscrowedPayment
    VendingMachine o-- State
    VendingMachine o-- ChangeMaker
    VendingMachine o-- HardwareAdapter
```

## Package layout

```
com.vending
├── domain/
│   ├── Money.java
│   ├── Currency.java
│   ├── Denomination.java
│   ├── DenominationKind.java
│   ├── SlotCode.java
│   ├── Product.java
│   ├── Slot.java
│   ├── InventoryView.java
│   ├── EscrowedPayment.java
│   └── AuditEvent.java
├── inventory/
│   ├── Inventory.java
│   └── CashInventory.java
├── payment/
│   ├── ChangeMaker.java
│   ├── GreedyChangeMaker.java
│   └── DpChangeMaker.java
├── state/
│   ├── State.java                 # sealed
│   ├── IdleState.java
│   ├── ProductSelectedState.java
│   ├── AcceptingPaymentState.java
│   ├── DispensingState.java
│   ├── MaintenanceState.java
│   └── OutOfServiceState.java
├── listener/
│   ├── AuditListener.java
│   └── ConsoleAuditListener.java
├── hardware/
│   ├── HardwareAdapter.java
│   └── StubHardware.java
├── VendingMachine.java
└── Main.java
```

## Why subclass-per-state instead of `enum State { IDLE, ... }`

- Each state has different valid operations. Enum forces every method to switch on the enum.
- Adding a new state (e.g., `RemoteHoldState`) means subclassing once, not editing 5 methods.
- The behavior **is** the type. That's the whole point of OOP.

The downside is more files. For a small problem (6 states) it's worth it; the clarity pays back at review time.

## Output

A clean separation: `VendingMachine` is a thin façade, `State` subclasses encode operation legality, services (`Inventory`, `CashInventory`, `ChangeMaker`, `HardwareAdapter`) carry the actual work.
