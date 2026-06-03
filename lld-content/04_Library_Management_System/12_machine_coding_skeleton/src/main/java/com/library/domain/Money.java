package com.library.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal a, String c) {
        this.amount = a.setScale(2, RoundingMode.HALF_UP);
        this.currency = c;
    }
    public static Money inr(double v) { return new Money(BigDecimal.valueOf(v), "INR"); }
    public static Money zero(String c) { return new Money(BigDecimal.ZERO, c); }

    public Money add(Money o) { check(o); return new Money(amount.add(o.amount), currency); }
    public Money subtract(Money o) { check(o); return new Money(amount.subtract(o.amount), currency); }
    public Money multiply(BigDecimal f) { return new Money(amount.multiply(f), currency); }
    public Money multiply(double f) { return multiply(BigDecimal.valueOf(f)); }
    public Money multiply(long f) { return multiply(BigDecimal.valueOf(f)); }
    public boolean isPositive() { return amount.signum() > 0; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    private void check(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
    }
    @Override public String toString() { return amount.toPlainString() + " " + currency; }
}
