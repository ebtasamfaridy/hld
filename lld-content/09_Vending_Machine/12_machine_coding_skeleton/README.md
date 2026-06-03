# 12 · Vending Machine — Machine Coding Skeleton

```
src/main/java/com/vending/
├── domain/      Money, Currency, Denomination, SlotCode, Product, Slot, EscrowedPayment, AuditEvent
├── inventory/   Inventory, CashInventory
├── payment/     ChangeMaker, GreedyChangeMaker
├── state/       State (sealed) + IdleState, ProductSelectedState, AcceptingPaymentState, DispensingState, MaintenanceState
├── listener/    AuditListener, ConsoleAuditListener
├── hardware/    HardwareAdapter, StubHardware
├── VendingMachine.java
└── Main.java
```

## Demo

1. Build a 6-slot machine; load 3 products.
2. Load cash float with denominations.
3. Buy a ₹35 product with ₹100 → expect change of ₹65.
4. Try to buy with insufficient cash float for change → expect refund + alert.
5. Operator enters maintenance, refills, exits.
