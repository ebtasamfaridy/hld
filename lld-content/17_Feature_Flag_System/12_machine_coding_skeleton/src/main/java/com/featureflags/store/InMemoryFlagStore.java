package com.featureflags.store;

import com.featureflags.core.Flag;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryFlagStore implements FlagStore {

    private final ConcurrentHashMap<String, Flag> map = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    @Override
    public Optional<Flag> get(String key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public void put(Flag flag) {
        // Only accept newer versions to handle out-of-order updates.
        map.merge(flag.key(), flag, (cur, neu) -> neu.version() > cur.version() ? neu : cur);
        version.incrementAndGet();
    }

    @Override
    public void remove(String key) {
        map.remove(key);
        version.incrementAndGet();
    }

    @Override
    public long version() { return version.get(); }
}
