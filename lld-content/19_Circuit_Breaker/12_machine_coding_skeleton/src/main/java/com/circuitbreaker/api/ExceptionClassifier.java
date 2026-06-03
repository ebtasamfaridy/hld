package com.circuitbreaker.api;

@FunctionalInterface
public interface ExceptionClassifier {
    boolean isFailure(Throwable t);

    static ExceptionClassifier all() { return t -> true; }

    @SafeVarargs
    static ExceptionClassifier ignoring(Class<? extends Throwable>... ignored) {
        return t -> {
            for (Class<? extends Throwable> c : ignored) {
                if (c.isInstance(t)) return false;
            }
            return true;
        };
    }
}
