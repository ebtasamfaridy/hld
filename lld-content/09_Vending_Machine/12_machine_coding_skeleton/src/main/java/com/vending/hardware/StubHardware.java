package com.vending.hardware;

import com.vending.domain.Denomination;
import com.vending.domain.SlotCode;

import java.util.Map;

public final class StubHardware implements HardwareAdapter {
    @Override
    public void dispense(SlotCode slot) {
        System.out.println("  [hw] dispensing slot " + slot);
    }
    @Override
    public void returnCoins(Map<Denomination, Integer> coins) {
        System.out.println("  [hw] returning coins: " + coins);
    }
}
