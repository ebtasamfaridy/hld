package com.bookmyshow.repository;

import com.bookmyshow.domain.Show;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public interface ShowRepository {
    void add(Show s);
    Optional<Show> get(String id);

    final class InMemory implements ShowRepository {
        private final Map<String, Show> map = new HashMap<>();
        @Override public void add(Show s)            { map.put(s.id(), s); }
        @Override public Optional<Show> get(String id) { return Optional.ofNullable(map.get(id)); }
    }
}
