package com.featureflags.client;

import com.featureflags.api.EvaluationContext;
import com.featureflags.api.EvaluationResult;
import com.featureflags.api.Reason;
import com.featureflags.core.Flag;
import com.featureflags.core.Prereq;
import com.featureflags.core.Rule;
import com.featureflags.core.Variation;
import com.featureflags.store.FlagStore;

public final class Evaluator {

    private final Bucketing bucketing;

    public Evaluator(Bucketing bucketing) { this.bucketing = bucketing; }

    public EvaluationResult evaluate(Flag flag, EvaluationContext ctx, FlagStore store) {
        if (!flag.enabled()) {
            return offResult(flag, Reason.OFF);
        }
        for (Prereq p : flag.prerequisites()) {
            Flag prereq = store.get(p.prerequisiteFlagKey()).orElse(null);
            if (prereq == null) return offResult(flag, Reason.PREREQUISITE_FAILED);
            EvaluationResult sub = evaluate(prereq, ctx, store);
            if (!p.expectedVariationId().equals(sub.variationId())) {
                return offResult(flag, Reason.PREREQUISITE_FAILED);
            }
        }
        for (Rule rule : flag.targetingRules()) {
            if (!rule.conditionsMatch(ctx)) continue;

            switch (rule.kind()) {
                case FIXED -> {
                    Variation v = flag.variation(rule.variationId());
                    return EvaluationResult.ruleMatch(v.id(), v.value(), rule.id());
                }
                case PERCENTAGE -> {
                    int b = bucketing.bucket(flag.key(), ctx.userId());
                    int threshold = rule.rolloutPercentage() * 100;
                    if (b < threshold) {
                        Variation v = flag.variation(rule.variationId());
                        return EvaluationResult.ruleMatch(v.id(), v.value(), rule.id());
                    }
                    // else: fall through to next rule
                }
            }
        }
        Variation fall = flag.variation(flag.fallthroughVariationId());
        return EvaluationResult.of(fall.id(), fall.value(), Reason.FALLTHROUGH);
    }

    private EvaluationResult offResult(Flag flag, Reason reason) {
        Variation off = flag.variation(flag.offVariationId());
        return EvaluationResult.of(off.id(), off.value(), reason);
    }
}
