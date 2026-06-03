package com.hotelbooking.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency;
    }
    public static Money inr(double v) { return new Money(BigDecimal.valueOf(v), "INR"); }
    public static Money zero(String c) { return new Money(BigDecimal.ZERO, c); }
    public static Money of(BigDecimal v, String c) { return new Money(v, c); }

    public Money add(Money o) { check(o); return new Money(amount.add(o.amount), currency); }
    public Money subtract(Money o) { check(o); return new Money(amount.subtract(o.amount), currency); }
    public Money multiply(BigDecimal f) { return new Money(amount.multiply(f), currency); }
    public Money multiply(double f) { return multiply(BigDecimal.valueOf(f)); }
    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isNegative() { return amount.signum() < 0; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }

    private void check(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
    }
    @Override public String toString() { return amount.toPlainString() + " " + currency; }
}
