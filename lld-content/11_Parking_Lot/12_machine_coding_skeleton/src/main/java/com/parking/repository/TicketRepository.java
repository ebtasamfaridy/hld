package com.parking.repository;

import com.parking.domain.Ticket;
import com.parking.domain.TicketId;

import java.util.Optional;

public interface TicketRepository {
    void save(Ticket t);
    Optional<Ticket> get(TicketId id);
}
