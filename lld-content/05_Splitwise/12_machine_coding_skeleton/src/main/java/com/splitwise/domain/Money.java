package com.splitwise.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private final long cents;       // store in minor units to avoid float drift
    private final String currency;

    private Money(long cents, String currency) {
        this.cents = cents;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        long c = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Money(c, currency);
    }
    public static Money inr(double v) { return of(BigDecimal.valueOf(v), "INR"); }
    public static Money usd(double v) { return of(BigDecimal.valueOf(v), "USD"); }
    public static Money fromCents(long cents, String currency) { return new Money(cents, currency); }
    public static Money zero(String c) { return new Money(0, c); }

    public Money add(Money o) { check(o); return new Money(cents + o.cents, currency); }
    public Money subtract(Money o) { check(o); return new Money(cents - o.cents, currency); }
    public Money negate() { return new Money(-cents, currency); }
    public boolean isZero() { return cents == 0; }
    public boolean isPositive() { return cents > 0; }
    public boolean isNegative() { return cents < 0; }
    public long cents() { return cents; }
    public BigDecimal amount() { return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2); }
    public String currency() { return currency; }

    private void check(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
    }
    @Override public String toString() { return amount().toPlainString() + " " + currency; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Money m)) return false;
        return cents == m.cents && currency.equals(m.currency);
    }
    @Override public int hashCode() { return Long.hashCode(cents) ^ currency.hashCode(); }
}
