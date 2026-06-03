# 13 · Vending Machine — Extensions & Tradeoffs

## Extensions

### 1. Card / UPI / wallet payments
Add `PaymentProcessor` strategy. `CashProcessor` (V1), `CardProcessor`, `UpiProcessor`. State machine adds `AuthorizingState` between `ProductSelected` and `Dispensing` for non-cash methods.

### 2. Loyalty / discount codes
Pre-payment step: `applyCode("HELLO10")`. Validates against `DiscountService`. Adjusts price for the current transaction only.

### 3. Subscription cards (pre-paid balance)
`MembershipCard` with balance; tap-to-pay deducts from balance. Add `BalanceLowError` event.

### 4. IoT / fleet management
- Background `FleetSync` task uploads audit log + heartbeat.
- Remote commands: restart, lock, refill request alert.
- Out-of-stock alert thresholds.

### 5. Multi-product combo
Beyond V1 — meaningful state-machine change. Track multiple selectedSlots; price = sum.

### 6. Refrigeration / temperature monitoring
Hardware adapter exposes `temperature()`. Background watchdog enters Maintenance if out of bounds.

### 7. Dispenser sensor (verify dispense)
Optical sensor confirms a can fell. If commit succeeded but sensor didn't see a drop, re-attempt or refund.

### 8. Multi-currency
Money + Currency already abstracted. Add per-product currency. Operator chooses default.

### 9. Operator audit
Every operator action recorded with `operator_id`. Periodic audit reports per machine.

## Tradeoffs

### State subclasses vs enum + ifs

| Criterion | Subclasses | Enum |
| --- | --- | --- |
| Operation legality per state | clean | repetitive ifs |
| Adding state | one new file | edits to every method |
| Lines of code | more files, less per file | one file, longer methods |
| Decision | **Subclasses** ✓ |

### Greedy vs DP change-making

| Criterion | Greedy | DP |
| --- | --- | --- |
| Canonical denominations | optimal | optimal |
| Non-canonical | wrong | optimal |
| Time | O(D) | O(D × amount) |
| Space | O(D) | O(amount) |
| Decision | **Greedy + DP fallback strategy** ✓ |

### Auto-refund on hardware error?

**No.** A motor-jam mid-dispense is ambiguous; the can may have fallen. Auto-refunding hands out free product. Operator escalation > automated refund.

### Single-threaded controller vs multi-threaded

| Criterion | Single | Multi |
| --- | --- | --- |
| Race conditions | none | many |
| Latency | <1 ms per event | 0.1ms with locks |
| Code complexity | trivial | hard |
| Decision | **Single-threaded event loop** ✓ |

### Local SQLite vs in-memory only

Local SQLite gives crash safety + audit replay for ~5 MB cost. Mandatory for any production machine.

## Open questions

- Operator force-cancel during a paying customer? (V1: reject; V2: configurable.)
- Note rejection: refund as note-out, or as coins? (Hardware constraint.)
- Tax / GST handling for receipts? (Compliance.)

## Output

```
Extensions:    cash → card/UPI/wallet, loyalty, subscriptions, IoT, combos
Pre-decided:   subclass states, greedy + DP fallback, no auto-refund on HW error,
               single-threaded loop, local SQLite audit
Open Qs:       force-cancel, note vs coin refunds, tax compliance
```
