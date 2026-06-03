package com.parking.allocation;

import com.parking.domain.Compatibility;
import com.parking.domain.Spot;
import com.parking.domain.TicketId;
import com.parking.domain.Vehicle;
import com.parking.repository.SpotRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Iterates spots in (floor asc, row asc, col asc) — i.e., closest to entrance first.
 * Adjusts ranking by EV preference for EV cars.
 */
public final class NearestEntranceAllocation implements AllocationStrategy {
    @Override
    public Optional<Spot> allocate(SpotRepository spots, Vehicle v, TicketId ticketId) {
        List<Spot> ordered = new java.util.ArrayList<>(spots.all());
        ordered.sort(Comparator.<Spot>comparingInt(s -> Compatibility.preferenceCost(v.type(), s.type()))
                .thenComparingInt(Spot::floor)
                .thenComparingInt(Spot::row)
                .thenComparingInt(Spot::col));

        for (Spot s : ordered) {
            if (s.isOccupied() || s.isOutOfService()) continue;
            if (!Compatibility.canPark(v.type(), s.type(), v.handicapPermit())) continue;
            if (s.tryClaim(ticketId)) return Optional.of(s);
            // else CAS lost; keep looking
        }
        return Optional.empty();
    }
}
