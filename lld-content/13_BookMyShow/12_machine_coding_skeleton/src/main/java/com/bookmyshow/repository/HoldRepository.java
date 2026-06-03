package com.bookmyshow.repository;

import com.bookmyshow.domain.Hold;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface HoldRepository {
    void save(Hold h);
    Optional<Hold> get(String id);

    final class InMemory implements HoldRepository {
        private final Map<String, Hold> map = new HashMap<>();
        @Override public void save(Hold h)            { map.put(h.id(), h); }
        @Override public Optional<Hold> get(String id) { return Optional.ofNullable(map.get(id)); }
    }
}
