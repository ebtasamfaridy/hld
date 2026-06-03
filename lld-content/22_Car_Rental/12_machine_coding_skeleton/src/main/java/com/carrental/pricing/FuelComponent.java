package com.carrental.pricing;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.catalog.VehicleModel;
import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

/**
 * Fuel deduction: charge for the difference between pickup-fuel% and return-fuel%
 * at a flat per-litre rate of the configured currency.
 *
 * For demo simplicity we use a hard-coded ₹100 per litre.
 */
public final class FuelComponent implements PricingComponent {
    private static final long PER_LITRE_MINOR = 10000;   // ₹100/L

    private final CatalogService catalog;
    public FuelComponent(CatalogService catalog) { this.catalog = catalog; }

    @Override public String name() { return "fuel"; }
    @Override public Money compute(Reservation r, Trip t) {
        if (t.fuelEndPct() == null) return Money.zero(r.baseFare().currency());
        int dropPct = t.fuelStartPct() - t.fuelEndPct();
        if (dropPct <= 0) return Money.zero(r.baseFare().currency());

        Vehicle v = catalog.requireVehicle(r.vehicleId());
        VehicleModel m = catalog.requireModel(v.modelId());
        long litresUsed = Math.round((m.fuelTankLitres() * dropPct) / 100.0);
        return Money.fromMinor(litresUsed * PER_LITRE_MINOR, r.baseFare().currency());
    }
}
