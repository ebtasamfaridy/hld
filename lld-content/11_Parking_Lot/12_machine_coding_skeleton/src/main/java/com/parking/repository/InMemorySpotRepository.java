package com.parking.repository;

import com.parking.domain.Spot;
import com.parking.domain.SpotId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySpotRepository implements SpotRepository {
    private final Map<SpotId, Spot> byId = new HashMap<>();
    private final List<Spot> ordered = new ArrayList<>();

    @Override public void add(Spot s)              { byId.put(s.id(), s); ordered.add(s); }
    @Override public Optional<Spot> get(SpotId id) { return Optional.ofNullable(byId.get(id)); }
    @Override public List<Spot> all()              { return List.copyOf(ordered); }
}
