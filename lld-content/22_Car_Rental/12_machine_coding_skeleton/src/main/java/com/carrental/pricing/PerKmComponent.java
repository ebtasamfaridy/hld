package com.carrental.pricing;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.catalog.VehicleModel;
import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

public final class PerKmComponent implements PricingComponent {
    private final CatalogService catalog;
    public PerKmComponent(CatalogService catalog) { this.catalog = catalog; }

    @Override public String name() { return "per_km"; }
    @Override public Money compute(Reservation r, Trip t) {
        if (t.odoEndKm() == null || t.odoEndKm() <= t.odoStartKm()) {
            return Money.zero(r.baseFare().currency());
        }
        long km = t.odoEndKm() - t.odoStartKm();
        Vehicle v = catalog.requireVehicle(r.vehicleId());
        VehicleModel m = catalog.requireModel(v.modelId());
        return m.perKmRate().multiply(km);
    }
}
