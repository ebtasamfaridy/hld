package com.circuitbreaker.api;

public enum State {
    CLOSED, OPEN, HALF_OPEN, FORCED_OPEN, FORCED_CLOSED, DISABLED
}
