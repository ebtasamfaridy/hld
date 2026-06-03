package com.vending.hardware;

import com.vending.domain.Denomination;
import com.vending.domain.SlotCode;

import java.util.Map;

public interface HardwareAdapter {
    void dispense(SlotCode slot);                              // can throw HardwareException
    void returnCoins(Map<Denomination, Integer> coins);        // for refund / change
}
