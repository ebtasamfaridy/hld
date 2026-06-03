package com.circuitbreaker.api;

import java.time.Duration;

public final class CircuitBreakerConfig {

    public final String name;
    public final int windowSize;                  // count-based: # of calls
    public final int minimumCalls;                 // before evaluating
    public final double failureRateThreshold;      // 0..1
    public final double slowCallRateThreshold;     // 0..1
    public final Duration slowCallThreshold;
    public final Duration waitDurationInOpen;
    public final int permittedCallsInHalfOpen;
    public final ExceptionClassifier classifier;

    private CircuitBreakerConfig(Builder b) {
        this.name = b.name;
        this.windowSize = b.windowSize;
        this.minimumCalls = b.minimumCalls;
        this.failureRateThreshold = b.failureRateThreshold;
        this.slowCallRateThreshold = b.slowCallRateThreshold;
        this.slowCallThreshold = b.slowCallThreshold;
        this.waitDurationInOpen = b.waitDurationInOpen;
        this.permittedCallsInHalfOpen = b.permittedCallsInHalfOpen;
        this.classifier = b.classifier;
    }

    public static Builder named(String name) { return new Builder(name); }

    public static final class Builder {
        private final String name;
        private int windowSize = 100;
        private int minimumCalls = 10;
        private double failureRateThreshold = 0.5;
        private double slowCallRateThreshold = 1.0;
        private Duration slowCallThreshold = Duration.ofSeconds(2);
        private Duration waitDurationInOpen = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpen = 10;
        private ExceptionClassifier classifier = ExceptionClassifier.all();

        private Builder(String name) { this.name = name; }
        public Builder windowSize(int n)               { this.windowSize = n; return this; }
        public Builder minimumCalls(int n)             { this.minimumCalls = n; return this; }
        public Builder failureRateThreshold(double r)  { this.failureRateThreshold = r; return this; }
        public Builder slowCallRateThreshold(double r) { this.slowCallRateThreshold = r; return this; }
        public Builder slowCallThreshold(Duration d)   { this.slowCallThreshold = d; return this; }
        public Builder waitDurationInOpen(Duration d)  { this.waitDurationInOpen = d; return this; }
        public Builder permittedCallsInHalfOpen(int n) { this.permittedCallsInHalfOpen = n; return this; }
        public Builder classifier(ExceptionClassifier c) { this.classifier = c; return this; }
        public CircuitBreakerConfig build()            { return new CircuitBreakerConfig(this); }
    }
}
