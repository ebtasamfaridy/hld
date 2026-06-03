package com.featureflags.api;

public record EvaluationResult(String variationId, Object value, Reason reason, String matchedRuleId) {

    public static EvaluationResult of(String variationId, Object value, Reason reason) {
        return new EvaluationResult(variationId, value, reason, null);
    }
    public static EvaluationResult ruleMatch(String variationId, Object value, String ruleId) {
        return new EvaluationResult(variationId, value, Reason.RULE_MATCH, ruleId);
    }
}
