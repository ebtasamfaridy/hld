# 06 · Circuit Breaker — API Design

## Library API

```java
public interface CircuitBreaker {
    String name();
    State state();
    Metrics metrics();
    Config config();

    /** Decorate a Supplier; throws CallNotPermittedException if rejected. */
    <T> Supplier<T> decorate(Supplier<T> supplier);

    /** Convenience: execute(supplier) = decorate(supplier).get(). */
    <T> T execute(Supplier<T> supplier) throws Throwable;
    void execute(Runnable runnable) throws Throwable;

    /** Direct lifecycle (used by decorate internally). */
    AcquireResult acquire();
    void onSuccess(long durationNanos);
    void onError(long durationNanos, Throwable t);
    void onRejected();

    /** Manual control. */
    void transitionToOpen();
    void transitionToClosed();
    void transitionToDisabled();
    void reset();

    /** Observability. */
    Closeable subscribe(EventListener listener);
}
```

## Builder / Registry

```java
public final class CircuitBreakerConfig {
    public static Builder custom();

    public static final class Builder {
        public Builder name(String name);
        public Builder slidingWindow(WindowKind kind, int size);
        public Builder failureRateThreshold(double pct);   // 0..100 or 0..1, document
        public Builder slowCallRateThreshold(double pct);
        public Builder slowCallThreshold(Duration d);
        public Builder waitDurationInOpenState(Duration d);
        public Builder permittedCallsInHalfOpenState(int n);
        public Builder minimumNumberOfCalls(int n);
        public Builder exceptionClassifier(ExceptionClassifier c);
        public CircuitBreakerConfig build();
    }
}

public final class CircuitBreakerRegistry {
    public static CircuitBreakerRegistry of(CircuitBreakerConfig defaultConfig);
    public CircuitBreaker breaker(String name);
    public CircuitBreaker breaker(String name, CircuitBreakerConfig config);
    public Collection<CircuitBreaker> all();
}
```

## Decorate other primitives

```java
public final class Decorators {
    public static <T> Supplier<T> ofSupplier(CircuitBreaker cb, Supplier<T> s);
    public static <T,R> Function<T,R> ofFunction(CircuitBreaker cb, Function<T,R> f);

    public static <T> Supplier<T> ofSupplier(
        CircuitBreaker cb, Bulkhead bh, Retry r, Timeout t, Supplier<T> s);
}
```

The combined `Decorators` builder applies in the right order:
**Bulkhead → CircuitBreaker → Retry → Timeout → underlying**.

## CallNotPermittedException

```java
public final class CallNotPermittedException extends RuntimeException {
    public CallNotPermittedException(String breakerName);
}
```

When the breaker is OPEN (or HALF_OPEN with no permits), the decorated supplier throws this. Callers can catch and apply a fallback.

## Fallback API

```java
public final class FallbackDecorator {
    public static <T> Supplier<T> withFallback(Supplier<T> primary, Function<Throwable,T> fallback);
}
```

Combined:
```java
Supplier<User> fetch = Decorators.ofSupplier(cb, retryConfig, () -> userClient.get(id))
    .withFallback(t -> User.empty());
```

## ExceptionClassifier

```java
public interface ExceptionClassifier {
    boolean isFailure(Throwable t);

    static ExceptionClassifier all() { return t -> true; }
    static ExceptionClassifier ignoring(Class<? extends Throwable>... ignored) { ... }
}
```

Not all `RuntimeException`s should trip the breaker. A `ValidationException` (4xx logical) shouldn't.

## Observability

```java
public interface EventListener {
    default void onStateChange(State from, State to, String reason) {}
    default void onCallSuccess(long durationNs) {}
    default void onCallFailure(long durationNs, Throwable t) {}
    default void onCallSlow(long durationNs) {}
    default void onCallRejected() {}
}
```

Adapters push to:
- Prometheus / Micrometer metrics.
- Structured log.
- OpenTelemetry traces (annotate spans with `cb.state`).

## Errors

| Error | Meaning |
| --- | --- |
| `CallNotPermittedException` | Breaker rejected the call |
| `BulkheadFullException` | Concurrency limit reached |
| `IllegalStateException` | Misuse: calling onSuccess after rejected acquire |

## Output

```
Library:    decorate(Supplier) → CallNotPermittedException on reject;
            execute(Supplier) for fluent use
Builder:    Config builder + Registry per name
Combined:   Bulkhead → CircuitBreaker → Retry → Timeout decorator order
Fallback:   wrap primary with fallback function
Listeners:  events for state changes + calls (for metrics, logs)
```
