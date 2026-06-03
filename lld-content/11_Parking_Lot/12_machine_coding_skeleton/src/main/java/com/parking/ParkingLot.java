package com.parking;

import com.parking.allocation.AllocationStrategy;
import com.parking.domain.*;
import com.parking.gateway.EntryResult;
import com.parking.listener.LotListener;
import com.parking.pricing.PricingStrategy;
import com.parking.repository.SpotRepository;
import com.parking.repository.TicketRepository;

import java.time.Clock;
import java.util.Optional;

public final class ParkingLot {

    private final SpotRepository spots;
    private final TicketRepository tickets;
    private final AllocationStrategy allocation;
    private final PricingStrategy pricing;
    private final LotListener listener;
    private final Clock clock;

    public ParkingLot(SpotRepository spots, TicketRepository tickets,
                      AllocationStrategy allocation, PricingStrategy pricing,
                      LotListener listener, Clock clock) {
        this.spots = spots; this.tickets = tickets;
        this.allocation = allocation; this.pricing = pricing;
        this.listener = listener; this.clock = clock;
    }

    public EntryResult requestEntry(Vehicle v) {
        TicketId id = TicketId.newId();
        Optional<Spot> chosen = allocation.allocate(spots, v, id);
        if (chosen.isEmpty()) {
            listener.onRejected(v, "lot full for type " + v.type());
            return new EntryResult.LotFull(v.type());
        }
        Spot s = chosen.get();
        Ticket t = new Ticket(id, v.plate(), v.type(), s.id(), clock.instant());
        tickets.save(t);
        listener.onAdmitted(v, s, t);
        return new EntryResult.Admitted(id, s.id());
    }

    public Money quote(TicketId id) {
        Ticket t = tickets.get(id).orElseThrow();
        Spot s = spots.get(t.spot()).orElseThrow();
        return pricing.compute(t, clock.instant(), s.type());
    }

    public void settle(TicketId id, String paymentRef) {
        Ticket t = tickets.get(id).orElseThrow();
        if (t.status() != Ticket.Status.ACTIVE) return;     // idempotent
        Spot s = spots.get(t.spot()).orElseThrow();
        Money fee = pricing.compute(t, clock.instant(), s.type());
        t.close(clock.instant(), fee, paymentRef);
        s.release(t.id());
        listener.onClosed(t, fee);
    }
}
