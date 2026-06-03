package com.carrental.pricing;

import com.carrental.domain.Money;
import com.carrental.reservation.Reservation;
import com.carrental.trip.Trip;

import java.util.ArrayList;
import java.util.List;

public final class CompositePricing {

    public record Line(String name, Money amount) {}
    public record Breakdown(List<Line> lines, Money total) {}

    private final List<PricingComponent> components;

    public CompositePricing(List<PricingComponent> components) {
        this.components = List.copyOf(components);
    }

    public Breakdown breakdown(Reservation r, Trip t) {
        String currency = r.baseFare().currency();
        Money total = Money.zero(currency);
        List<Line> lines = new ArrayList<>();
        for (PricingComponent c : components) {
            Money m = c.compute(r, t);
            // Always include the line for transparency, even if zero.
            lines.add(new Line(c.name(), m));
            total = total.add(m);
        }
        return new Breakdown(List.copyOf(lines), total);
    }
}
