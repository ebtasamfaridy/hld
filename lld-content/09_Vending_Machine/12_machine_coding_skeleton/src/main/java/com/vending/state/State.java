package com.vending.state;

import com.vending.VendingMachine;
import com.vending.domain.Denomination;
import com.vending.domain.SlotCode;

public sealed interface State
        permits IdleState, ProductSelectedState, AcceptingPaymentState,
                DispensingState, MaintenanceState {

    default void selectProduct(VendingMachine m, SlotCode slot) { reject("selectProduct"); }
    default void insertCoin(VendingMachine m, Denomination d)   { reject("insertCoin"); }
    default void cancel(VendingMachine m)                        { reject("cancel"); }
    default void enterMaintenance(VendingMachine m)              { reject("enterMaintenance"); }
    default void exitMaintenance(VendingMachine m)               { reject("exitMaintenance"); }

    default void reject(String op) {
        throw new IllegalStateException(op + " not valid in " + this.getClass().getSimpleName());
    }

    String name();
}
