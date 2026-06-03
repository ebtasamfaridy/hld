package com.carrental.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {
    private final long minor;
    private final String currency;

    private Money(long minor, String currency) {
        this.minor = minor;
        this.currency = currency;
    }

    public static Money inr(double rupees) { return of(BigDecimal.valueOf(rupees), "INR"); }
    public static Money zero(String currency) { return new Money(0, currency); }
    public static Money of(BigDecimal amount, String currency) {
        long m = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Money(m, currency);
    }
    public static Money fromMinor(long minor, String currency) { return new Money(minor, currency); }

    public Money add(Money o) { check(o); return new Money(minor + o.minor, currency); }
    public Money subtract(Money o) { check(o); return new Money(minor - o.minor, currency); }
    public Money multiply(long n) { return new Money(minor * n, currency); }
    public boolean isPositive() { return minor > 0; }
    public boolean greaterThan(Money o) { check(o); return minor > o.minor; }

    public long minor() { return minor; }
    public String currency() { return currency; }

    private void check(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Money m)) return false;
        return minor == m.minor && currency.equals(m.currency);
    }
    @Override public int hashCode() { return Objects.hash(minor, currency); }
    @Override public String toString() {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2).toPlainString() + " " + currency;
    }
}
