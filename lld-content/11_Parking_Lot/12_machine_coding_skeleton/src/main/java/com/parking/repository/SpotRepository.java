package com.parking.repository;

import com.parking.domain.Spot;
import com.parking.domain.SpotId;

import java.util.List;
import java.util.Optional;

public interface SpotRepository {
    void add(Spot s);
    Optional<Spot> get(SpotId id);
    List<Spot> all();                 // ordered for deterministic strategy iteration
}
