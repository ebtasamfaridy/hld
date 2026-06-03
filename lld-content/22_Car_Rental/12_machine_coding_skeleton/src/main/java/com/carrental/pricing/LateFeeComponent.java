package com.carrental.pricing;

import com.carrental.catalog.CatalogService;
import com.carrental.catalog.Vehicle;
import com.carrental.catalog.VehicleModel;
import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

import java.time.Duration;

/**
 * Late fee: free first 30 minutes after booked end, then 2× hourly rate per started hour.
 */
public final class LateFeeComponent implements PricingComponent {
    private static final long GRACE_MINUTES = 30;
    private static final long MULTIPLIER = 2;

    private final CatalogService catalog;
    public LateFeeComponent(CatalogService catalog) { this.catalog = catalog; }

    @Override public String name() { return "late_fee"; }
    @Override public Money compute(Reservation r, Trip t) {
        if (t.returnedAt() == null) return Money.zero(r.baseFare().currency());
        Duration overage = Duration.between(r.window().end(), t.returnedAt());
        long minutesLate = overage.toMinutes();
        if (minutesLate <= GRACE_MINUTES) return Money.zero(r.baseFare().currency());

        long billable = minutesLate - GRACE_MINUTES;
        long lateHours = (billable + 59) / 60;   // ceil
        Vehicle v = catalog.requireVehicle(r.vehicleId());
        VehicleModel m = catalog.requireModel(v.modelId());
        return m.hourlyRate().multiply(MULTIPLIER * lateHours);
    }
}
