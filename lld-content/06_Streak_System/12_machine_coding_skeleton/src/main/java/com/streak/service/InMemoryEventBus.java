package com.streak.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InMemoryEventBus implements EventBus {

    private final Map<Class<? extends DomainEvent>, List<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();

    @Override
    public void publish(DomainEvent event) {
        var ls = listeners.get(event.getClass());
        if (ls == null) return;
        for (Consumer<?> c : ls) {
            @SuppressWarnings("unchecked")
            Consumer<DomainEvent> typed = (Consumer<DomainEvent>) c;
            try {
                typed.accept(event);
            } catch (RuntimeException e) {
                System.err.println("[bus] listener error: " + e.getMessage());
            }
        }
    }

    @Override
    public <T extends DomainEvent> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
}
