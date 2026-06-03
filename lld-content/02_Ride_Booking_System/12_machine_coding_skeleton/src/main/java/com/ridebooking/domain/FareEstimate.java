package com.ridebooking.domain;

import java.math.BigDecimal;

public final class FareEstimate {
    private final Money base;
    private final Money perKmRate;
    private final Money perMinuteRate;
    private final BigDecimal surgeFactor;
    private final Money min;
    private final Money max;

    public FareEstimate(Money base, Money perKm, Money perMin,
                        BigDecimal surgeFactor, Money min, Money max) {
        this.base = base; this.perKmRate = perKm; this.perMinuteRate = perMin;
        this.surgeFactor = surgeFactor; this.min = min; this.max = max;
    }
    public Money base() { return base; }
    public Money perKmRate() { return perKmRate; }
    public Money perMinuteRate() { return perMinuteRate; }
    public BigDecimal surgeFactor() { return surgeFactor; }
    public Money min() { return min; }
    public Money max() { return max; }

    @Override public String toString() {
        return "Estimate{min=" + min + ", max=" + max + ", surge=" + surgeFactor + "}";
    }
}
