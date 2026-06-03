package com.featureflags.api;

import com.featureflags.client.Evaluator;
import com.featureflags.core.Flag;
import com.featureflags.store.FlagStore;

public final class FeatureFlagClient implements FeatureFlags {

    private final FlagStore store;
    private final Evaluator evaluator;

    public FeatureFlagClient(FlagStore store, Evaluator evaluator) {
        this.store = store;
        this.evaluator = evaluator;
    }

    @Override
    public boolean isOn(String key, EvaluationContext ctx, boolean defaultValue) {
        Object v = variationValue(key, ctx, defaultValue);
        return v instanceof Boolean b ? b : defaultValue;
    }

    @Override
    public String variation(String key, EvaluationContext ctx, String defaultValue) {
        Object v = variationValue(key, ctx, defaultValue);
        return v == null ? defaultValue : v.toString();
    }

    @Override
    public Object variationValue(String key, EvaluationContext ctx, Object defaultValue) {
        return evaluate(key, ctx, defaultValue).value();
    }

    @Override
    public EvaluationResult evaluate(String key, EvaluationContext ctx, Object defaultValue) {
        Flag flag = store.get(key).orElse(null);
        if (flag == null) return EvaluationResult.of(null, defaultValue, Reason.FLAG_NOT_FOUND);
        try {
            return evaluator.evaluate(flag, ctx, store);
        } catch (RuntimeException e) {
            // never throw to caller
            return EvaluationResult.of(null, defaultValue, Reason.DEFAULT);
        }
    }
}
