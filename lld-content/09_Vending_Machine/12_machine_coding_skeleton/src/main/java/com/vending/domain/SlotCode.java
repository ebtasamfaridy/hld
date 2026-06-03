package com.vending.domain;

import java.util.Objects;

public record SlotCode(String value) {
    public SlotCode { Objects.requireNonNull(value); if (value.isBlank()) throw new IllegalArgumentException(); }
    public static SlotCode of(String s) { return new SlotCode(s); }
    @Override public String toString() { return value; }
}
