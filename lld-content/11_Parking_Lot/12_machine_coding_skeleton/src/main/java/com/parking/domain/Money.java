package com.parking.domain;

public record Money(long minor, String currency) {
    public static Money inr(long paise) { return new Money(paise, "INR"); }
    public Money plus(Money o)        { return new Money(minor + o.minor, currency); }
    public Money times(long n)        { return new Money(minor * n, currency); }
    @Override public String toString() { return currency + "(" + minor + ")"; }
}
