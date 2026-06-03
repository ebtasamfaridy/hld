package com.vending.state;

import com.vending.VendingMachine;

public final class MaintenanceState implements State {
    @Override
    public void exitMaintenance(VendingMachine m) {
        m.audit("MAINTENANCE_EXITED", "operator");
        m.transitionTo(new IdleState());
    }
    @Override public String name() { return "MAINTENANCE"; }
}
