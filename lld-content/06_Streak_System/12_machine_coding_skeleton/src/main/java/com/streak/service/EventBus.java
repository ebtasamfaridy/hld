package com.streak.service;

import java.util.function.Consumer;

public interface EventBus {
    void publish(DomainEvent event);
    <T extends DomainEvent> void subscribe(Class<T> type, Consumer<T> listener);
}
