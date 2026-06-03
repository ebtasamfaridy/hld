package com.fooddelivery.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Tiny in-process pub/sub. */
public final class EventBus {
    private final ConcurrentMap<Class<?>, List<Consumer<?>>> subs = new ConcurrentHashMap<>();

    public <E> void register(Class<E> type, Consumer<E> handler) {
        subs.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        List<Consumer<?>> list = subs.getOrDefault(event.getClass(), List.of());
        for (Consumer<?> raw : list) ((Consumer<E>) raw).accept(event);
    }
}
