package com.circuitbreaker.registry;

import com.circuitbreaker.api.CircuitBreaker;
import com.circuitbreaker.api.CircuitBreakerConfig;
import com.circuitbreaker.core.StandardCircuitBreaker;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CircuitBreakerRegistry {

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreaker breaker(CircuitBreakerConfig config) {
        return breakers.computeIfAbsent(config.name, n -> new StandardCircuitBreaker(config));
    }

    public CircuitBreaker get(String name) { return breakers.get(name); }
    public Collection<CircuitBreaker> all()  { return breakers.values(); }
}
