package com.ridebooking.pricing;

import com.ridebooking.domain.FareEstimate;
import com.ridebooking.domain.FareFinal;
import com.ridebooking.domain.Location;
import com.ridebooking.domain.Money;
import com.ridebooking.domain.RideType;

import java.math.BigDecimal;

public final class PricingService {
    private final SurgeService surge;
    private final Money baseFare;
    private final Money perKm;
    private final Money perMinute;
    private final Money platformFee;
    private final BigDecimal taxPct;

    public PricingService(SurgeService surge, Money baseFare, Money perKm,
                          Money perMinute, Money platformFee, BigDecimal taxPct) {
        this.surge = surge;
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.perMinute = perMinute;
        this.platformFee = platformFee;
        this.taxPct = taxPct;
    }

    public FareEstimate estimate(Location pickup, Location drop, RideType type) {
        double km = pickup.distanceKm(drop);
        int minutes = (int) Math.max(1, km * 2);   // toy ETA: 30 km/h avg
        BigDecimal s = surge.factor(pickup, type);
        Money subtotal = baseFare
                .add(perKm.multiply(BigDecimal.valueOf(km)))
                .add(perMinute.multiply(BigDecimal.valueOf(minutes)));
        Money surgeAmt = subtotal.multiply(s.subtract(BigDecimal.ONE));
        Money mid = subtotal.add(surgeAmt).add(platformFee);
        Money tax = mid.multiply(taxPct);
        Money total = mid.add(tax);
        // ±15% range
        Money min = total.multiply(0.85);
        Money max = total.multiply(1.15);
        return new FareEstimate(baseFare, perKm, perMinute, s, min, max);
    }

    public FareFinal finalize(double actualKm, int actualMin, BigDecimal lockedSurge,
                              Money tip) {
        Money distance = perKm.multiply(BigDecimal.valueOf(actualKm));
        Money time     = perMinute.multiply(BigDecimal.valueOf(actualMin));
        Money subtotal = baseFare.add(distance).add(time);
        Money surgeAmt = subtotal.multiply(lockedSurge.subtract(BigDecimal.ONE));
        Money mid = subtotal.add(surgeAmt).add(platformFee);
        Money tax = mid.multiply(taxPct);
        Money total = mid.add(tax).add(tip);
        return new FareFinal(baseFare, distance, time, surgeAmt, platformFee, tax, tip, total);
    }
}
