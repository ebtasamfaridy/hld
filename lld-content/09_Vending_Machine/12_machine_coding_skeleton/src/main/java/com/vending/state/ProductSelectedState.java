package com.vending.state;

import com.vending.VendingMachine;
import com.vending.domain.Denomination;

public final class ProductSelectedState implements State {
    @Override
    public void insertCoin(VendingMachine m, Denomination d) {
        m.escrowAdd(d);
        m.audit("COIN_INSERTED", d.value().toString());
        m.transitionTo(new AcceptingPaymentState());
        // Re-trigger AcceptingPayment's evaluation in case the single coin >= price.
        new AcceptingPaymentState().evaluate(m);
    }

    @Override
    public void cancel(VendingMachine m) {
        m.clearSelection();
        m.audit("TXN_CANCELLED", "user");
        m.transitionTo(new IdleState());
    }

    @Override public String name() { return "PRODUCT_SELECTED"; }
}
