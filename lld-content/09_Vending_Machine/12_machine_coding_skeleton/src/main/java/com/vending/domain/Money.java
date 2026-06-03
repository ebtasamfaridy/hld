package com.vending.domain;

import java.util.Objects;

/** Always integer minor units. Currency-bound. */
public record Money(long minor, String currency) implements Comparable<Money> {
    public Money {
        Objects.requireNonNull(currency);
        if (minor < 0) throw new IllegalArgumentException("non-negative");
    }
    public static Money inr(long paise) { return new Money(paise, "INR"); }
    public Money plus(Money other)  { check(other); return new Money(minor + other.minor, currency); }
    public Money minus(Money other) { check(other); return new Money(minor - other.minor, currency); }
    public boolean ge(Money other)  { check(other); return minor >= other.minor; }
    public boolean lt(Money other)  { check(other); return minor <  other.minor; }
    private void check(Money o) { if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch"); }
    @Override public int compareTo(Money o) { check(o); return Long.compare(minor, o.minor); }
    public boolean isZero() { return minor == 0; }
    @Override public String toString() { return currency + "(" + minor + ")"; }
}
