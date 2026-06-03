package com.vending;

import com.vending.domain.*;
import com.vending.hardware.HardwareAdapter;
import com.vending.inventory.CashInventory;
import com.vending.inventory.Inventory;
import com.vending.listener.AuditListener;
import com.vending.payment.ChangeMaker;
import com.vending.state.DispensingState;
import com.vending.state.IdleState;
import com.vending.state.State;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

public final class VendingMachine {

    private final Inventory inventory;
    private final CashInventory cash;
    private final ChangeMaker changeMaker;
    private final HardwareAdapter hardware;
    private final AuditListener audit;
    private final Clock clock;

    private State state = new IdleState();
    private final EscrowedPayment escrow = new EscrowedPayment();
    private SlotCode selected;

    public VendingMachine(Inventory inv, CashInventory cash, ChangeMaker cm,
                          HardwareAdapter hw, AuditListener audit, Clock clock) {
        this.inventory = inv;
        this.cash = cash;
        this.changeMaker = cm;
        this.hardware = hw;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- Customer surface (delegated to State) ----
    public void selectProduct(SlotCode s)      { state.selectProduct(this, s); }
    public void insertCoin(Denomination d)     { state.insertCoin(this, d); }
    public void cancel()                       { state.cancel(this); }

    // ---- Operator ----
    public void enterMaintenance()              { state.enterMaintenance(this); }
    public void exitMaintenance()               { state.exitMaintenance(this); }
    public void refillSlot(SlotCode s, int delta, Money price, Product fallback) {
        require(state.name().equals("MAINTENANCE"), "must be in maintenance");
        inventory.refill(s, delta, price, fallback);
        audit("OPERATOR_REFILL", s + " +" + delta);
    }
    public void refillCash(Denomination d, int delta) {
        require(state.name().equals("MAINTENANCE"), "must be in maintenance");
        cash.add(d, delta);
        audit("OPERATOR_REFILL_CASH", d.value() + " x" + delta);
    }
    public Money collectCash() {
        require(state.name().equals("MAINTENANCE"), "must be in maintenance");
        Money m = cash.drainAll();
        audit("OPERATOR_COLLECT", m.toString());
        return m;
    }

    // ---- Used by State subclasses ----
    public void transitionTo(State next) {
        this.state = next;
    }
    public State state() { return state; }

    public boolean isInStock(SlotCode s) {
        return inventory.slot(s).map(slot -> slot.count() > 0).orElse(false);
    }
    public void setSelectedSlot(SlotCode s) { this.selected = s; }
    public void clearSelection()            { this.selected = null; escrow.clear(); }

    public void escrowAdd(Denomination d)   { escrow.add(d); }
    public void refundEscrow()              {
        if (!escrow.isEmpty()) hardware.returnCoins(escrow.snapshotCoins());
        escrow.clear();
    }
    public boolean escrowMeetsPrice() {
        Slot s = inventory.slot(selected).orElseThrow();
        return escrow.total().ge(s.price());
    }

    /**
     * Atomic commit: dispense + change-out. If change cannot be made,
     * returns false WITHOUT mutating state — caller handles refund.
     */
    public boolean tryCommitPurchase() {
        Slot slot = inventory.slot(selected).orElseThrow();
        Money changeAmount = escrow.total().minus(slot.price());

        // Build a hypothetical post-deposit cash inventory to compute change against.
        Map<Denomination, Integer> hypothetical = new java.util.HashMap<>(cash.snapshot());
        for (var e : escrow.snapshotCoins().entrySet()) {
            hypothetical.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        Optional<Map<Denomination, Integer>> changeOpt =
                changeMaker.makeChange(changeAmount, hypothetical);
        if (changeOpt.isEmpty()) return false;

        Map<Denomination, Integer> change = changeOpt.get();

        transitionTo(new DispensingState());
        try {
            if (!inventory.tryDispense(selected)) return false;
            hardware.dispense(selected);
            if (!change.isEmpty()) hardware.returnCoins(change);
        } catch (RuntimeException hwError) {
            audit("HARDWARE_ERROR", hwError.getMessage());
            transitionTo(new com.vending.state.MaintenanceState());
            return true;   // do NOT auto-refund; alert operator
        }

        cash.addAll(escrow.snapshotCoins());
        if (!change.isEmpty()) cash.removeAll(change);

        audit("PRODUCT_DISPENSED", selected + " price=" + slot.price() + " change=" + changeAmount);
        clearSelection();
        transitionTo(new IdleState());
        return true;
    }

    public void audit(String kind, String payload) { audit.onEvent(clock.instant(), kind, payload); }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }
}
