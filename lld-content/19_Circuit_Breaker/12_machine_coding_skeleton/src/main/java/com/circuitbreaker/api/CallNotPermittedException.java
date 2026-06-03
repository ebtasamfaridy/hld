package com.circuitbreaker.api;

public final class CallNotPermittedException extends RuntimeException {
    public CallNotPermittedException(String breakerName) {
        super("CircuitBreaker '" + breakerName + "' is not permitting calls");
    }
}
