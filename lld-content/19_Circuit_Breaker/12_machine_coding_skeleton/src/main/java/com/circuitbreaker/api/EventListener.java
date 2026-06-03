package com.circuitbreaker.api;

public interface EventListener {
    default void onStateChange(String name, State from, State to, String reason) {}
    default void onCallSuccess(String name, long durationNs) {}
    default void onCallFailure(String name, long durationNs, Throwable t) {}
    default void onCallSlow   (String name, long durationNs) {}
    default void onCallRejected(String name) {}
}
