# 14 · Vending Machine — Interviewer Follow-ups

## Q1. "Walk me through the State pattern. Why subclasses?"

Each state has different *valid operations*. With an enum + `if (state == ...) { ... }` per method, every state addition forces edits across every method. With a sealed `State` subclass, operations are localized: `IdleState.selectProduct` is the only place that accepts a select; default rejects elsewhere. Adding `ScheduledMaintenanceState` is one new file, no edits to existing states.

Key word: **localizes change**.

---

## Q2. "Customer inserts ₹100 for a ₹35 item, but the machine has no ₹65 in change. What happens?"

Refund the ₹100 and stay at IDLE. Audit a `NO_CHANGE_AVAILABLE` event. Display "Insufficient change — please use exact change." Do NOT dispense; the alternative gives away free product.

In V2, we proactively *prevent* the customer from selecting the product if change is currently impossible (display a banner) — better UX.

---

## Q3. "Two customers tap simultaneously."

Single-threaded event loop. Both events queue; first wins, second errors out (state has already advanced). UI grays out the second tap.

Hardware-level (acceptor): each coin produces exactly one hardware event. No double-counting risk.

---

## Q4. "Power loss during dispense. What state is the machine in on reboot?"

Reboot sequence:
1. Replay audit log to determine last consistent state.
2. If last event is `PRODUCT_DISPENSED`, state = IDLE; nothing to recover.
3. If last event is `COIN_INSERTED` without subsequent `PRODUCT_DISPENSED` or `TXN_REFUNDED`, the customer's escrow is dangling — emit a `RECOVERY_ALERT` and enter `MaintenanceState`. Operator inspects.

We deliberately don't auto-refund a dangling transaction — too easy to abuse.

---

## Q5. "Why integer minor units for Money?"

Floats lie. `0.10 + 0.20 != 0.30`. We use `long minorUnits` (paise / cents). Conversions to display happen at the UI boundary, not in business logic.

---

## Q6. "Greedy vs DP for change-making."

Greedy: pick largest denomination that fits, recurse. Correct for canonical denominations (1, 2, 5, 10, 20, 50, 100, 500), wrong for arbitrary sets.

DP: classic min-coin problem; O(amount × denoms). Always correct.

V1: greedy default; DP fallback if denominations are configured non-canonical.

---

## Q7. "How does the change-maker know which denominations are available?"

It receives a `Map<Denomination, Integer> available`. It cannot pick more than what's there. The cash inventory is the truth; we **simulate the post-deposit state** (current cash + escrow) when computing change, so a coin the customer just inserted can be returned as change if it fits.

---

## Q8. "Multi-machine fleet: how does an operator manage 5000 machines?"

Cloud backend; each machine syncs audit log + heartbeat. Operator app shows alerts: out-of-stock slots, stuck coins, low cash. Remote commands (restart, lock) queue per machine and execute on next heartbeat.

Key constraint: machine-network failures must not break customer-side operation. Local audit is the truth.

---

## Q9. "What about card payments?"

`PaymentProcessor` strategy. `CardProcessor.authorize(amount, cardToken) → AuthResult`. Add an `AuthorizingState` between `ProductSelected` and `Dispensing`:
- Tap card → AuthorizingState → call gateway with timeout.
- Success → DispensingState.
- Decline / timeout → IdleState with `CARD_DECLINED` audit.

Don't change Cash code paths — orthogonal extension.

---

## Q10. "Most subtle bug a junior writes here?"

Two:
1. **Adding the customer's just-inserted coins to the cash float BEFORE attempting to make change** — this works in many cases but can falsely allow change if the inserted coins are needed back. Fix: simulate hypothetical post-deposit state.
2. **Auto-refund on hardware error.** Looks user-friendly; opens fraud surface. Always escalate to operator.

---

## Q11. "How would you test this?"

- **State transition tests**: `expectInState(IDLE).when(selectProduct).thenInState(PRODUCT_SELECTED)`.
- **Change-making property tests**: random denominations, random amounts; verify sum, count, availability.
- **Hardware error tests**: Mock `HardwareAdapter` throws; assert state = MAINTENANCE, audit recorded, no auto-refund.
- **Recovery tests**: write audit log to fixture, boot machine, assert correct recovered state.

---

## Q12. "Tradeoff between strict 'exact change' mode and 'best effort'."

Strict: refuse purchase if exact change can't be made. Best-effort: round down to available denominations and credit difference to a customer "wallet" (requires loyalty system).

V1 = strict. V2 = best-effort with consent ("Cannot make exact change. Refund or store as credit?").

---

## Q13. "Audit log gets too big. What do you do?"

Local SQLite log auto-rotates: keep last 7 days online; archive older to JSONL files; compress and upload to fleet backend. Local DB stays bounded.

---

## Q14. "How do you guarantee no double-charge across crashes?"

The commit step (dispense + cash mutate + audit write) is one SQLite transaction. Either all happens or none. The hardware action (`hardware.dispense`) is *outside* the SQL transaction by necessity — we mitigate by:
1. Doing hardware action **inside** the transaction in the audit DB.
2. Sensors confirming dispense.
3. Operator escalation for ambiguity.

This is the same problem as banking: the physical world action and the database action can't be made atomic without sensors.

---

## Q15. "What if the operator collects cash mid-customer transaction?"

V1: operator must be in MaintenanceState; if customer transaction is in flight, the operator command is rejected ("machine busy"). Operator waits or aborts the customer flow with explicit confirmation.

V2: operator can force-cancel with a confirmation dialog; the customer is refunded; audit records `OPERATOR_FORCE_CANCEL`.

---

## Output

```
Drill questions covered:
- State pattern justification
- No-change scenario
- Concurrency (single-threaded loop)
- Power-loss recovery (audit replay)
- Money type (integer minor units)
- Greedy vs DP change-making
- Card payment extension
- Common bugs (premature cash deposit; auto-refund on HW error)
- Testing
- Audit log retention
- Atomicity across hardware + DB
- Operator vs customer race
```
