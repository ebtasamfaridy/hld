package com.ratelimit.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class InMemoryStore<S> implements Store<S> {
    private final ConcurrentHashMap<String, S> map = new ConcurrentHashMap<>();

    @Override
    public S compute(String key, Function<S, S> updater) {
        return map.compute(key, (k, current) -> updater.apply(current));
    }

    @Override public S getOrNull(String key) { return map.get(key); }
}
