package com.scheduler.store;

import com.scheduler.core.Job;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryJobStore implements JobStore {
    private final ConcurrentHashMap<String, Job> map = new ConcurrentHashMap<>();
    @Override public void put(Job j)            { map.put(j.id(), j); }
    @Override public Optional<Job> get(String id) { return Optional.ofNullable(map.get(id)); }
    @Override public void remove(String id)     { map.remove(id); }
}
