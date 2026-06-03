package com.featureflags.store;

import com.featureflags.core.Flag;

import java.util.Optional;

public interface FlagStore {
    Optional<Flag> get(String key);
    void put(Flag flag);
    void remove(String key);
    long version();
}
