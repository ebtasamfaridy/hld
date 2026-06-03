package com.vending.state;

import com.vending.VendingMachine;
import com.vending.domain.SlotCode;

public final class IdleState implements State {
    @Override
    public void selectProduct(VendingMachine m, SlotCode slot) {
        if (!m.isInStock(slot)) {
            m.audit("OUT_OF_STOCK", slot.toString());
            return;
        }
        m.setSelectedSlot(slot);
        m.audit("PRODUCT_SELECTED", slot.toString());
        m.transitionTo(new ProductSelectedState());
    }

    @Override public void enterMaintenance(VendingMachine m) {
        m.audit("MAINTENANCE_ENTERED", "operator");
        m.transitionTo(new MaintenanceState());
    }

    @Override public String name() { return "IDLE"; }
}
