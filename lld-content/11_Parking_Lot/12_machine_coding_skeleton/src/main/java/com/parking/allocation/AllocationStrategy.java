package com.parking.allocation;

import com.parking.domain.Spot;
import com.parking.domain.TicketId;
import com.parking.domain.Vehicle;
import com.parking.repository.SpotRepository;

import java.util.Optional;

public interface AllocationStrategy {
    /** Picks a spot AND atomically claims it for ticketId. Returns empty if lot is full for type. */
    Optional<Spot> allocate(SpotRepository spots, Vehicle vehicle, TicketId ticketId);
}
