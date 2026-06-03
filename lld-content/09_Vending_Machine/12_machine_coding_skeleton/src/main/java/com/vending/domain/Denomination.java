package com.vending.domain;

public record Denomination(Money value, Kind kind) implements Comparable<Denomination> {
    public enum Kind { COIN, NOTE }

    public static Denomination coin(long paise) { return new Denomination(Money.inr(paise), Kind.COIN); }
    public static Denomination note(long paise) { return new Denomination(Money.inr(paise), Kind.NOTE); }

    @Override public int compareTo(Denomination o) { return value.compareTo(o.value); }
}
