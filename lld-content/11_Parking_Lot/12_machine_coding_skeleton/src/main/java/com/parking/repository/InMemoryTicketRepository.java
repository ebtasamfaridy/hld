package com.parking.repository;

import com.parking.domain.Ticket;
import com.parking.domain.TicketId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTicketRepository implements TicketRepository {
    private final Map<TicketId, Ticket> map = new HashMap<>();
    @Override public void save(Ticket t)          { map.put(t.id(), t); }
    @Override public Optional<Ticket> get(TicketId id) { return Optional.ofNullable(map.get(id)); }
}
