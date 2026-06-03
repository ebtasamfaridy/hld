package com.ridebooking.domain;

public final class FareFinal {
    private final Money base;
    private final Money distance;
    private final Money time;
    private final Money surge;
    private final Money platformFee;
    private final Money tax;
    private final Money tip;
    private final Money total;

    public FareFinal(Money base, Money distance, Money time, Money surge,
                     Money platformFee, Money tax, Money tip, Money total) {
        this.base = base; this.distance = distance; this.time = time; this.surge = surge;
        this.platformFee = platformFee; this.tax = tax; this.tip = tip; this.total = total;
    }
    public Money total() { return total; }
    @Override public String toString() {
        return "Fare{total=" + total + " (base=" + base + " dist=" + distance
             + " time=" + time + " surge=" + surge + " fee=" + platformFee + " tax=" + tax + ")}";
    }
}
