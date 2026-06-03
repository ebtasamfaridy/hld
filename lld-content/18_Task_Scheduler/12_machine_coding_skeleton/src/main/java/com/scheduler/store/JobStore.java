package com.scheduler.store;

import com.scheduler.core.Job;

import java.util.Optional;

public interface JobStore {
    void put(Job j);
    Optional<Job> get(String id);
    void remove(String id);
}
