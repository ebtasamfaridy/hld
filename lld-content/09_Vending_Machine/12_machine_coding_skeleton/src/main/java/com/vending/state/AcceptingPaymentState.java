package com.vending.state;

import com.vending.VendingMachine;
import com.vending.domain.Denomination;

public final class AcceptingPaymentState implements State {

    @Override
    public void insertCoin(VendingMachine m, Denomination d) {
        m.escrowAdd(d);
        m.audit("COIN_INSERTED", d.value().toString());
        evaluate(m);
    }

    @Override
    public void cancel(VendingMachine m) {
        m.refundEscrow();
        m.clearSelection();
        m.audit("TXN_CANCELLED", "user");
        m.transitionTo(new IdleState());
    }

    /** Decide whether to commit (escrow >= price AND change available) or wait for more coins. */
    public void evaluate(VendingMachine m) {
        if (!m.escrowMeetsPrice()) return;            // wait for more
        if (!m.tryCommitPurchase()) {
            // can't make change: refund and go back to Idle
            m.refundEscrow();
            m.audit("TXN_REFUNDED", "no_change_available");
            m.clearSelection();
            m.transitionTo(new IdleState());
        }
        // tryCommitPurchase already transitioned through Dispensing → Idle on success
    }

    @Override public String name() { return "ACCEPTING_PAYMENT"; }
}
