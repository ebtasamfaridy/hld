package com.featureflags.api;

public interface FeatureFlags {
    boolean isOn(String key, EvaluationContext ctx, boolean defaultValue);
    String  variation(String key, EvaluationContext ctx, String defaultValue);
    Object  variationValue(String key, EvaluationContext ctx, Object defaultValue);
    EvaluationResult evaluate(String key, EvaluationContext ctx, Object defaultValue);
}
