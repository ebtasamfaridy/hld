package com.vending.inventory;

import com.vending.domain.Money;
import com.vending.domain.Product;
import com.vending.domain.Slot;
import com.vending.domain.SlotCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Inventory {
    private final Map<SlotCode, Slot> slots = new LinkedHashMap<>();

    public void addSlot(Slot slot) { slots.put(slot.code(), slot); }

    public Optional<Slot> slot(SlotCode code) { return Optional.ofNullable(slots.get(code)); }

    public Map<SlotCode, Slot> all() { return Map.copyOf(slots); }

    public boolean tryDispense(SlotCode code) {
        Slot s = slots.get(code);
        if (s == null) return false;
        return s.tryDecrement();
    }

    public void refill(SlotCode code, int delta, Money price, Product fallback) {
        Slot s = slots.get(code);
        if (s == null) {
            slots.put(code, new Slot(code, fallback, price, delta));
        } else {
            s.increment(delta);
            s.setPrice(price);
        }
    }
}
